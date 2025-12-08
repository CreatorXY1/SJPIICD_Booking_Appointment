// functions/index.js
const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.firestore();

const DAILY_CAPACITY = 400; // shared constant used by slot logic

/**
 * 0) Basic user role claim on signup (existing)
 */
exports.setCustomClaimOnSignup = functions.auth.user().onCreate(async (user) => {
  const email = user.email || '';
  console.log('New user created:', user.uid, email);
  try {
    if (email.endsWith('@school.edu') || email.endsWith('@yourschool.edu')) {
      await admin.auth().setCustomUserClaims(user.uid, { role: 'student' });
      console.log('Set role=student for', user.uid);
    } else if (email === 'cashier@local.test') {
      await admin.auth().setCustomUserClaims(user.uid, { role: 'cashier' });
      console.log('Set role=cashier for', user.uid);
    } else {
      await admin.auth().setCustomUserClaims(user.uid, { role: 'guest' });
      console.log('Set role=guest for', user.uid);
    }
  } catch (err) {
    console.error('Failed to set custom claims for', user.uid, err);
  }
});

/**
 * 1) When appointment transitions to PAID (existing)
 */
exports.onAppointmentPaid = functions.firestore
  .document('appointments/{appointmentId}')
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after = change.after.data() || {};
    try {
      if (before.status !== 'PAID' && after.status === 'PAID') {
        console.log(`Appointment ${context.params.appointmentId} marked PAID`);
        console.log('Simulated email to:', after.userEmail, 'receiptUrl:', after.paymentReceiptUrl || 'none');
        if (!after.verifiedByFunction) {
          await change.after.ref.update({
            verifiedByFunction: true,
            verifiedAt: admin.firestore.FieldValue.serverTimestamp()
          });
        }
      }
    } catch (err) {
      console.error('onAppointmentPaid error', err);
    }
  });

/**
 * 2) Defensive: increment slot on appointment create
 */
exports.onAppointmentCreated = functions.firestore
  .document('appointments/{appointmentId}')
  .onCreate(async (snap, context) => {
    const appt = snap.data() || {};
    const date = appt.date;
    const window = appt.window;
    if (!date || !window) {
      console.log('Appointment missing date/window, skipping slot update:', context.params.appointmentId);
      return;
    }
    const slotId = `${date}_${window}`;
    const slotRef = db.collection('slots').doc(slotId);

    try {
      await db.runTransaction(async (tx) => {
        const s = await tx.get(slotRef);
        if (!s.exists) {
          tx.set(slotRef, {
            date: date,
            window: window,
            capacity: DAILY_CAPACITY,
            bookedCount: 1
          });
        } else {
          const bc = s.get('bookedCount') || 0;
          tx.update(slotRef, { bookedCount: bc + 1 });
        }
      });
      console.log('Slot incremented for', slotId);
    } catch (err) {
      console.error('Failed to increment slot for', slotId, err);
    }
  });

/**
 * 3) Defensive: decrement slot on appointment delete
 */
exports.onAppointmentDeleted = functions.firestore
  .document('appointments/{appointmentId}')
  .onDelete(async (snap, context) => {
    const appt = snap.data() || {};
    const date = appt.date;
    const window = appt.window;
    if (!date || !window) {
      console.log('Deleted appointment missing date/window, skipping slot update:', context.params.appointmentId);
      return;
    }
    const slotId = `${date}_${window}`;
    const slotRef = db.collection('slots').doc(slotId);

    try {
      await db.runTransaction(async (tx) => {
        const s = await tx.get(slotRef);
        if (!s.exists) {
          console.warn('Slot doc missing on delete for', slotId);
          return;
        }
        const bc = s.get('bookedCount') || 0;
        const next = Math.max(0, bc - 1);
        tx.update(slotRef, { bookedCount: next });
      });
      console.log('Slot decremented for', slotId);
    } catch (err) {
      console.error('Failed to decrement slot for', slotId, err);
    }
  });

/**
 * 4) Move booking when appointment updated (reschedule)
 *    Decrement old slot, check and increment new slot atomically.
 */
exports.onAppointmentUpdated = functions.firestore
  .document('appointments/{appointmentId}')
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after = change.after.data() || {};

    const oldDate = before.date;
    const oldWindow = before.window;
    const newDate = after.date;
    const newWindow = after.window;

    if (!oldDate || !oldWindow || !newDate || !newWindow) {
      console.log('Missing date/window values on update - skipping slot move for', context.params.appointmentId);
      return;
    }
    if (oldDate === newDate && oldWindow === newWindow) {
      console.log('Appointment update without schedule change for', context.params.appointmentId);
      return;
    }

    const oldSlotId = `${oldDate}_${oldWindow}`;
    const newSlotId = `${newDate}_${newWindow}`;
    const oldSlotRef = db.collection('slots').doc(oldSlotId);
    const newSlotRef = db.collection('slots').doc(newSlotId);
    const apptRef = change.after.ref;

    try {
      await db.runTransaction(async (tx) => {
        const oldSnap = await tx.get(oldSlotRef);
        const newSnap = await tx.get(newSlotRef);

        if (!oldSnap.exists) {
          throw new Error('OLD_SLOT_MISSING');
        }
        const oldCount = oldSnap.get('bookedCount') || 0;

        let newCount = 0;
        let newCapacity = DAILY_CAPACITY;
        if (newSnap.exists) {
          newCount = newSnap.get('bookedCount') || 0;
          newCapacity = newSnap.get('capacity') || DAILY_CAPACITY;
        }

        if (newCount >= newCapacity) {
          // We throw a Firestore-style error to indicate aborted (client can map this)
          throw new functions.https.HttpsError('aborted', 'NEW_SLOT_FULL');
        }

        tx.update(oldSlotRef, { bookedCount: Math.max(0, oldCount - 1) });

        if (!newSnap.exists) {
          tx.set(newSlotRef, {
            date: newDate,
            window: newWindow,
            capacity: newCapacity,
            bookedCount: newCount + 1
          });
        } else {
          tx.update(newSlotRef, { bookedCount: newCount + 1 });
        }

        tx.update(apptRef, {
          lastRescheduledAt: admin.firestore.FieldValue.serverTimestamp()
        });
      });

      console.log(`Moved appointment ${context.params.appointmentId} from ${oldSlotId} -> ${newSlotId}`);
    } catch (err) {
      // Log error — do not rethrow (optional)
      console.error('Failed to move appointment', context.params.appointmentId, err);
    }
  });

/**
 * 5) Username reservation API (callable) - reserveUsername
 *    Client must be authenticated (context.auth) so we can bind username -> uid.
 *    This creates document: /usernames/{lowercase_username} => { uid, createdAt }
 */
exports.reserveUsername = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Must be signed in to reserve username');
  }
  const uid = context.auth.uid;
  let username = (data && data.username) ? String(data.username).trim().toLowerCase() : '';
  if (!username) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing username');
  }
  if (username.length < 3 || username.length > 30) {
    throw new functions.https.HttpsError('invalid-argument', 'Username length must be 3..30');
  }
  // sanitize allowed characters (letters, numbers, dot, underscore, dash)
  if (!/^[a-z0-9._-]+$/.test(username)) {
    throw new functions.https.HttpsError('invalid-argument', 'Username contains invalid characters');
  }

  const usernameRef = db.collection('usernames').doc(username);

  try {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(usernameRef);
      if (snap.exists) {
        throw new functions.https.HttpsError('already-exists', 'Username already taken');
      }
      tx.set(usernameRef, { uid: uid, createdAt: admin.firestore.FieldValue.serverTimestamp() });
    });
    return { ok: true };
  } catch (e) {
    if (e instanceof functions.https.HttpsError) throw e;
    console.error('reserveUsername internal error', e);
    throw new functions.https.HttpsError('internal', 'reserve failed');
  }
});

/**
 * 6) releaseUsername callable - remove username mapping (only owner can delete)
 */
exports.releaseUsername = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Must be signed in to release username');
  }
  const uid = context.auth.uid;
  const username = (data && data.username) ? String(data.username).trim().toLowerCase() : '';
  if (!username) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing username');
  }
  const usernameRef = db.collection('usernames').doc(username);

  try {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(usernameRef);
      if (!snap.exists) return;
      const doc = snap.data();
      if (!doc || doc.uid !== uid) {
        throw new functions.https.HttpsError('permission-denied', 'You do not own this username');
      }
      tx.delete(usernameRef);
    });
    return { ok: true };
  } catch (e) {
    if (e instanceof functions.https.HttpsError) throw e;
    console.error('releaseUsername internal error', e);
    throw new functions.https.HttpsError('internal', 'release failed');
  }
});
