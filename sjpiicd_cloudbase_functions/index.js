// functions/index.js
const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = admin.firestore();

// -------------------------------------------
// CLOUDINARY CONFIG (for permit uploads)
// -------------------------------------------
const cloudinary = require("cloudinary").v2;

const cloudinaryConfig = functions.config().cloudinary;

if (cloudinaryConfig && cloudinaryConfig.cloud_name) {
  cloudinary.config({
    cloud_name: cloudinaryConfig.cloud_name,
    api_key: cloudinaryConfig.api_key,
    api_secret: cloudinaryConfig.api_secret
  });
  console.log("Cloudinary configured successfully");
} else {
  console.warn("WARNING: Cloudinary not configured. Set via: firebase functions:config:set cloudinary.cloud_name=...");
}

// -------------------------------------------
// CONSTANTS
// -------------------------------------------
const DAILY_CAPACITY = 400;
const MAX_APPOINTMENTS_PER_USER = 5;

// -------------------------------------------
// HELPER FUNCTIONS
// -------------------------------------------

async function checkAppointmentLimit(userId) {
  const snapshot = await db.collection('appointments')
    .where('userId', '==', userId)
    .where('status', 'in', ['PENDING', 'PAID', 'APPROVED'])
    .get();

  return snapshot.size < MAX_APPOINTMENTS_PER_USER;
}

function isValidDate(dateString) {
  const regex = /^\d{4}-\d{2}-\d{2}$/;
  if (!regex.test(dateString)) return false;

  const date = new Date(dateString);
  return date instanceof Date && !isNaN(date);
}

function isValidTimeWindow(window) {
  const validWindows = [
    '09:00-10:00',
    '10:00-11:00',
    '11:00-12:00',
    '13:00-14:00',
    '14:00-15:00'
  ];
  return validWindows.includes(window);
}

// Helper to get current timestamp as ISO string
function getCurrentTimestamp() {
  return new Date().toISOString();
}

// =====================================================
// AUTH TRIGGER: Set Custom Claims on Signup
// =====================================================
if (functions && functions.auth && typeof functions.auth.user === 'function') {
  exports.setCustomClaimOnSignup = functions.auth.user().onCreate(async (user) => {
    const email = user.email || "";
    try {
      let role = "guest";

      if (email.endsWith("@school.edu") || email.endsWith("@yourschool.edu")) {
        role = "student";
      } else if (email === "cashier@local.test") {
        role = "cashier";
      } else if (email === "admin@local.test") {
        role = "admin";
      }

      await admin.auth().setCustomUserClaims(user.uid, { role });

      await db.collection('users').doc(user.uid).set({
        email: user.email,
        displayName: user.displayName || null,
        role: role,
        createdAt: getCurrentTimestamp()
      }, { merge: true });

      console.log(`User ${user.uid} created with role: ${role}`);
    } catch (err) {
      console.error("Failed to set custom claim:", err);
    }
  });
} else {
  console.log("Skipping setCustomClaimOnSignup registration: functions.auth.user() not available");
}

// =====================================================
// FIRESTORE TRIGGER: Appointment Paid
// =====================================================
if (functions && functions.firestore && typeof functions.firestore.document === 'function') {
  exports.onAppointmentPaid = functions.firestore
    .document("appointments/{appointmentId}")
    .onUpdate(async (change, context) => {
      const before = change.before.data() || {};
      const after = change.after.data() || {};

      if (before.status !== "PAID" && after.status === "PAID") {
        console.log(`Appointment ${context.params.appointmentId} marked PAID`);

        if (!after.verifiedByFunction) {
          await change.after.ref.update({
            verifiedByFunction: true,
            verifiedAt: getCurrentTimestamp()
          });

          console.log(`Email would be sent for appointment ${context.params.appointmentId}`);
        }
      }
    });
} else {
  console.log("Skipping onAppointmentPaid registration");
}

// =====================================================
// FIRESTORE TRIGGER: Appointment Created
// =====================================================
if (functions && functions.firestore && typeof functions.firestore.document === 'function') {
  exports.onAppointmentCreated = functions.firestore
    .document("appointments/{appointmentId}")
    .onCreate(async (snap) => {
      const appt = snap.data() || {};
      const date = appt.date;
      const window = appt.window;

      if (appt.createdByFunction === true) {
        console.log(`Skipping slot increment for ${snap.id} (createdByFunction)`);
        return;
      }

      if (!date || !window) {
        console.warn(`Appointment ${snap.id} missing date or window`);
        return;
      }

      const slotId = `${date}_${window}`;
      const slotRef = db.collection("slots").doc(slotId);

      try {
        await db.runTransaction(async (tx) => {
          const s = await tx.get(slotRef);
          if (!s.exists) {
            tx.set(slotRef, {
              date,
              window,
              capacity: DAILY_CAPACITY,
              bookedCount: 1,
              createdAt: getCurrentTimestamp()
            });
          } else {
            const currentCount = s.get("bookedCount") || 0;
            tx.update(slotRef, {
              bookedCount: currentCount + 1,
              updatedAt: getCurrentTimestamp()
            });
          }
        });
        console.log(`Slot ${slotId} incremented for appointment ${snap.id}`);
      } catch (err) {
        console.error(`Failed to increment slot ${slotId}:`, err);
      }
    });
} else {
  console.log("Skipping onAppointmentCreated registration");
}

// =====================================================
// FIRESTORE TRIGGER: Appointment Updated (Rescheduled)
// =====================================================
if (functions && functions.firestore && typeof functions.firestore.document === 'function') {
  exports.onAppointmentUpdated = functions.firestore
    .document("appointments/{appointmentId}")
    .onUpdate(async (change, context) => {
      const before = change.before.data() || {};
      const after = change.after.data() || {};

      const oldDate = before.date;
      const oldWindow = before.window;
      const newDate = after.date;
      const newWindow = after.window;

      if (!oldDate || !oldWindow || !newDate || !newWindow) return;
      if (oldDate === newDate && oldWindow === newWindow) return;

      const oldSlotId = `${oldDate}_${oldWindow}`;
      const newSlotId = `${newDate}_${newWindow}`;

      const oldSlotRef = db.collection("slots").doc(oldSlotId);
      const newSlotRef = db.collection("slots").doc(newSlotId);

      try {
        await db.runTransaction(async (tx) => {
          const oldSnap = await tx.get(oldSlotRef);
          const newSnap = await tx.get(newSlotRef);

          if (!oldSnap.exists) {
            throw new Error("OLD_SLOT_MISSING");
          }

          const oldCount = oldSnap.get("bookedCount") || 0;
          let newCount = newSnap.exists ? newSnap.get("bookedCount") || 0 : 0;
          const capacity = newSnap.exists ? newSnap.get("capacity") || DAILY_CAPACITY : DAILY_CAPACITY;

          if (newCount >= capacity) {
            throw new functions.https.HttpsError("aborted", "NEW_SLOT_FULL");
          }

          tx.update(oldSlotRef, {
            bookedCount: Math.max(0, oldCount - 1),
            updatedAt: getCurrentTimestamp()
          });

          if (!newSnap.exists) {
            tx.set(newSlotRef, {
              date: newDate,
              window: newWindow,
              capacity: DAILY_CAPACITY,
              bookedCount: 1,
              createdAt: getCurrentTimestamp()
            });
          } else {
            tx.update(newSlotRef, {
              bookedCount: newCount + 1,
              updatedAt: getCurrentTimestamp()
            });
          }

          tx.update(change.after.ref, {
            lastRescheduledAt: getCurrentTimestamp()
          });
        });

        console.log(`Appointment ${context.params.appointmentId} rescheduled from ${oldSlotId} to ${newSlotId}`);
      } catch (err) {
        console.error("Failed to reschedule appointment:", err);
      }
    });
} else {
  console.log("Skipping onAppointmentUpdated registration");
}

// =====================================================
// CALLABLE: Reserve Username
// =====================================================
exports.reserveUsername = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Must be signed in");
  }

  const uid = context.auth.uid;
  const username = String(data.username || "").trim().toLowerCase();

  if (!/^[a-z0-9._-]{3,30}$/.test(username)) {
    throw new functions.https.HttpsError("invalid-argument", "Username must be 3-30 characters (lowercase letters, numbers, ., _, -)");
  }

  const reserved = ['admin', 'root', 'system', 'cashier', 'support'];
  if (reserved.includes(username)) {
    throw new functions.https.HttpsError("invalid-argument", "Username is reserved");
  }

  const usernameRef = db.collection("usernames").doc(username);

  try {
    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(usernameRef);
      if (snap.exists) {
        throw new functions.https.HttpsError("already-exists", "Username taken");
      }

      const userRecord = await admin.auth().getUser(uid);

      tx.set(usernameRef, {
        uid,
        email: userRecord.email || null,
        createdAt: getCurrentTimestamp()
      });

      return { username };
    });

    console.log(`Username '${username}' reserved for user ${uid}`);
    return { ok: true, username: result.username };
  } catch (err) {
    if (err instanceof functions.https.HttpsError) throw err;
    console.error("reserveUsername error:", err);
    throw new functions.https.HttpsError("internal", "Failed to reserve username");
  }
});

// =====================================================
// CALLABLE: Release Username
// =====================================================
exports.releaseUsername = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Must be signed in");
  }

  const uid = context.auth.uid;
  const username = String(data.username || "").trim().toLowerCase();

  if (!username) {
    throw new functions.https.HttpsError("invalid-argument", "Missing username");
  }

  const usernameRef = db.collection("usernames").doc(username);

  try {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(usernameRef);
      if (!snap.exists) return;

      if (snap.data().uid !== uid) {
        throw new functions.https.HttpsError("permission-denied", "Not your username");
      }

      tx.delete(usernameRef);
    });

    console.log(`Username '${username}' released by user ${uid}`);
    return { ok: true };
  } catch (err) {
    if (err instanceof functions.https.HttpsError) throw err;
    console.error("releaseUsername error:", err);
    throw new functions.https.HttpsError("internal", "Failed to release username");
  }
});

// =====================================================
// CALLABLE: Get Email for Username
// =====================================================
exports.getEmailForUsername = functions.https.onCall(async (data, context) => {
  const username = String(data.username || "").trim().toLowerCase();

  console.log(`[getEmailForUsername] Looking up username: ${username}`);

  if (!username) {
    throw new functions.https.HttpsError("invalid-argument", "Missing username");
  }

  try {
    const usernameRef = db.collection("usernames").doc(username);
    const snap = await usernameRef.get();

    if (!snap.exists) {
      throw new functions.https.HttpsError("not-found", "Username not found");
    }

    const data = snap.data();
    const uid = data.uid;

    if (data.email) {
      console.log(`[getEmailForUsername] Found email in username doc: ${data.email}`);
      return { email: data.email };
    }

    if (!uid) {
      throw new functions.https.HttpsError("not-found", "No uid for username");
    }

    const userRecord = await admin.auth().getUser(uid);
    const email = userRecord.email || null;

    if (!email) {
      throw new functions.https.HttpsError("not-found", "Email not found");
    }

    console.log(`[getEmailForUsername] Found email via Auth: ${email}`);
    return { email };
  } catch (err) {
    if (err instanceof functions.https.HttpsError) throw err;
    console.error("[getEmailForUsername] error:", err);
    throw new functions.https.HttpsError("internal", "Failed to lookup email");
  }
});

// =====================================================
// CALLABLE: Upload Permit (Admin Only)
// =====================================================
exports.uploadPermit = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Must be signed in");
  }

  const uid = context.auth.uid;

  const userDoc = await db.collection("users").doc(uid).get();
  if (!userDoc.exists || userDoc.data().role !== "admin") {
    throw new functions.https.HttpsError("permission-denied", "Admin access required");
  }

  const targetUid = data.targetUid;
  const base64Image = data.base64Image;

  if (!targetUid || !base64Image) {
    throw new functions.https.HttpsError("invalid-argument", "Missing targetUid or base64Image");
  }

  if (!cloudinaryConfig || !cloudinaryConfig.cloud_name) {
    throw new functions.https.HttpsError("failed-precondition", "Cloudinary not configured");
  }

  try {
    const uploadRes = await cloudinary.uploader.upload(base64Image, {
      folder: "permits",
      resource_type: "image"
    });

    const permitUrl = uploadRes.secure_url;

    await db.collection("users").doc(targetUid).set(
      {
        clearance: {
          permitUrl,
          permitReady: true,
          permitUpdatedAt: getCurrentTimestamp()
        }
      },
      { merge: true }
    );

    console.log(`Permit uploaded for user ${targetUid}: ${permitUrl}`);
    return { ok: true, permitUrl };
  } catch (err) {
    console.error("Cloudinary upload error:", err);
    throw new functions.https.HttpsError("internal", "Upload failed: " + err.message);
  }
});

// =====================================================
// CALLABLE: Create Appointment (Atomic) - COMPLETE FIX
// =====================================================
exports.createAppointment = functions.https.onCall(async (data, context) => {
  console.log("[createAppointment] Called with data:", JSON.stringify(data));
  console.log("[createAppointment] Context auth:", context?.auth?.uid || "unknown");

  if (!context || !context.auth) {
    console.error("[createAppointment] Authentication failed");
    throw new functions.https.HttpsError('unauthenticated', 'Must be signed in');
  }

  const uid = context.auth.uid;
  const date = String(data.date || '').trim();
  const window = String(data.window || '').trim();

  console.log(`[createAppointment] Parsed - uid: ${uid}, date: ${date}, window: ${window}`);

  if (!date || !window) {
    console.error("[createAppointment] Missing date or window");
    throw new functions.https.HttpsError('invalid-argument', 'Missing date or window');
  }

  if (!isValidDate(date)) {
    console.error("[createAppointment] Invalid date format:", date);
    throw new functions.https.HttpsError('invalid-argument', 'Invalid date format. Use YYYY-MM-DD');
  }

  if (!isValidTimeWindow(window)) {
    console.error("[createAppointment] Invalid time window:", window);
    throw new functions.https.HttpsError('invalid-argument', 'Invalid time window');
  }

  const appointmentDate = new Date(date);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  if (appointmentDate < today) {
    console.error("[createAppointment] Date is in the past:", date);
    throw new functions.https.HttpsError('invalid-argument', 'Cannot book appointments in the past');
  }

  console.log("[createAppointment] Checking appointment limit...");

  try {
    const canBook = await checkAppointmentLimit(uid);
    if (!canBook) {
      console.error("[createAppointment] User exceeded appointment limit");
      throw new functions.https.HttpsError('resource-exhausted', `Maximum ${MAX_APPOINTMENTS_PER_USER} active appointments allowed`);
    }
    console.log("[createAppointment] Appointment limit check passed");
  } catch (err) {
    console.error("[createAppointment] Error checking appointment limit:", err);
    throw err;
  }

  const slotId = `${date}_${window}`;
  const apptId = `${uid}_${slotId}`;
  const apptRef = db.collection('appointments').doc(apptId);
  const slotRef = db.collection('slots').doc(slotId);

  console.log(`[createAppointment] Starting transaction - apptId: ${apptId}, slotId: ${slotId}`);

  try {
    const result = await db.runTransaction(async (tx) => {
      console.log("[createAppointment] Inside transaction - checking existing appointment");

      const apptSnap = await tx.get(apptRef);
      if (apptSnap.exists) {
        const existingStatus = apptSnap.data().status;
        console.log(`[createAppointment] Existing appointment found with status: ${existingStatus}`);

        if (existingStatus !== 'REJECTED' && existingStatus !== 'CANCELLED') {
          throw new functions.https.HttpsError('already-exists', 'You already have an appointment for this slot');
        }
        console.log("[createAppointment] Existing appointment is REJECTED/CANCELLED, allowing rebooking");
      }

      console.log("[createAppointment] Checking slot availability");

      const slotSnap = await tx.get(slotRef);

      let bookedCount = 0;
      let capacity = DAILY_CAPACITY;

      if (slotSnap.exists) {
        bookedCount = slotSnap.get('bookedCount') || 0;
        capacity = slotSnap.get('capacity') || DAILY_CAPACITY;
        console.log(`[createAppointment] Slot exists - bookedCount: ${bookedCount}, capacity: ${capacity}`);
      } else {
        console.log("[createAppointment] Slot does not exist, will create new");
      }

      if (bookedCount >= capacity) {
        console.error("[createAppointment] Slot is full");
        throw new functions.https.HttpsError('resource-exhausted', 'Selected slot is full');
      }

      console.log("[createAppointment] Creating appointment document");

      const now = getCurrentTimestamp();

      const apptData = {
        userId: uid,
        date: date,
        window: window,
        status: 'PENDING',
        paymentMethod: 'PAY_AT_SCHOOL',
        createdAt: now,
        createdByFunction: true
      };

      tx.set(apptRef, apptData);
      console.log("[createAppointment] Appointment document set");

      if (!slotSnap.exists) {
        console.log("[createAppointment] Creating new slot");
        tx.set(slotRef, {
          date: date,
          window: window,
          capacity: capacity,
          bookedCount: 1,
          createdAt: now
        });
      } else {
        console.log("[createAppointment] Updating existing slot");
        tx.update(slotRef, {
          bookedCount: bookedCount + 1,
          updatedAt: now
        });
      }

      console.log("[createAppointment] Transaction operations complete");
      return { appointmentId: apptId };
    });

    console.log(`[createAppointment] Transaction committed successfully: ${result.appointmentId}`);
    return { ok: true, appointmentId: result.appointmentId };

  } catch (err) {
    console.error("[createAppointment] Transaction failed with error:", err);
    console.error("[createAppointment] Error name:", err.name);
    console.error("[createAppointment] Error message:", err.message);
    console.error("[createAppointment] Error stack:", err.stack);

    if (err instanceof functions.https.HttpsError) {
      throw err;
    }

    throw new functions.https.HttpsError('internal', 'Failed to create appointment: ' + err.message);
  }
});

// =====================================================
// TEST ENDPOINT (Development Only)
// =====================================================
exports.helloWorld = functions.https.onRequest((req, res) => {
  res.json({
    message: 'Hello from Firebase Functions!',
    timestamp: new Date().toISOString(),
    environment: process.env.FUNCTIONS_EMULATOR ? 'emulator' : 'production'
  });
});