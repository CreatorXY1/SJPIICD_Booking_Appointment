// functions/index.js
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

// -------------------------------------------
// CLOUDINARY CONFIG  (used for permit uploads)
// -------------------------------------------
const cloudinary = require("cloudinary").v2;

cloudinary.config({
  cloud_name: "YOUR_CLOUD_NAME",
  api_key: "YOUR_API_KEY",
  api_secret: "YOUR_API_SECRET"
});

// -------------------------------------------
// CONSTANTS
// -------------------------------------------
const DAILY_CAPACITY = 400;


// =====================================================
// 0) Set Custom User Claims On Signup
// =====================================================
exports.setCustomClaimOnSignup = functions.auth.user().onCreate(async (user) => {
  const email = user.email || "";
  try {
    if (email.endsWith("@school.edu") || email.endsWith("@yourschool.edu")) {
      await admin.auth().setCustomUserClaims(user.uid, { role: "student" });
    } else if (email === "cashier@local.test") {
      await admin.auth().setCustomUserClaims(user.uid, { role: "cashier" });
    } else {
      await admin.auth().setCustomUserClaims(user.uid, { role: "guest" });
    }
  } catch (err) {
    console.error("Failed to set custom claim:", err);
  }
});


// =====================================================
// 1) Firestore: Appointment → PAID triggers email (simulated)
// =====================================================
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
          verifiedAt: admin.firestore.FieldValue.serverTimestamp()
        });
      }
    }
  });


// =====================================================
// 2) Appointment Created → Increment corresponding slot
// =====================================================
exports.onAppointmentCreated = functions.firestore
  .document("appointments/{appointmentId}")
  .onCreate(async (snap) => {
    const appt = snap.data() || {};
    const date = appt.date;
    const window = appt.window;

    if (!date || !window) return;

    const slotId = `${date}_${window}`;
    const slotRef = db.collection("slots").doc(slotId);

    await db.runTransaction(async (tx) => {
      const s = await tx.get(slotRef);
      if (!s.exists) {
        tx.set(slotRef, {
          date,
          window,
          capacity: DAILY_CAPACITY,
          bookedCount: 1
        });
      } else {
        tx.update(slotRef, { bookedCount: (s.get("bookedCount") || 0) + 1 });
      }
    });
  });


// =====================================================
// 3) Appointment Deleted → Decrement slot
// =====================================================
exports.onAppointmentDeleted = functions.firestore
  .document("appointments/{appointmentId}")
  .onDelete(async (snap) => {
    const appt = snap.data() || {};
    const date = appt.date;
    const window = appt.window;

    if (!date || !window) return;

    const slotId = `${date}_${window}`;
    const slotRef = db.collection("slots").doc(slotId);

    await db.runTransaction(async (tx) => {
      const s = await tx.get(slotRef);
      if (!s.exists) return;

      const count = s.get("bookedCount") || 0;
      tx.update(slotRef, { bookedCount: Math.max(0, count - 1) });
    });
  });


// =====================================================
// 4) Appointment Updated → Move slot booking
// =====================================================
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

        if (!oldSnap.exists) throw new Error("OLD_SLOT_MISSING");

        const oldCount = oldSnap.get("bookedCount") || 0;
        let newCount = newSnap.exists ? newSnap.get("bookedCount") || 0 : 0;

        if (newCount >= DAILY_CAPACITY) {
          throw new functions.https.HttpsError("aborted", "NEW_SLOT_FULL");
        }

        // Decrement old slot
        tx.update(oldSlotRef, { bookedCount: Math.max(0, oldCount - 1) });

        // Increment new slot
        if (!newSnap.exists) {
          tx.set(newSlotRef, {
            date: newDate,
            window: newWindow,
            capacity: DAILY_CAPACITY,
            bookedCount: 1
          });
        } else {
          tx.update(newSlotRef, { bookedCount: newCount + 1 });
        }

        tx.update(change.after.ref, {
          lastRescheduledAt: admin.firestore.FieldValue.serverTimestamp()
        });
      });
    } catch (err) {
      console.error("Failed to move slot:", err);
    }
  });


// =====================================================
// 5) reserveUsername() — callable
// =====================================================
exports.reserveUsername = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated");
  }

  const uid = context.auth.uid;
  const username = String(data.username || "").trim().toLowerCase();

  if (!/^[a-z0-9._-]{3,30}$/.test(username)) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid username");
  }

  const usernameRef = db.collection("usernames").doc(username);

  try {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(usernameRef);
      if (snap.exists) {
        throw new functions.https.HttpsError("already-exists", "Taken");
      }
      tx.set(usernameRef, {
        uid,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
    });
    return { ok: true };
  } catch (err) {
    if (err instanceof functions.https.HttpsError) throw err;
    throw new functions.https.HttpsError("internal");
  }
});


// =====================================================
// 6) releaseUsername() — callable
// =====================================================
exports.releaseUsername = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated");

  const uid = context.auth.uid;
  const username = String(data.username || "").trim().toLowerCase();

  const usernameRef = db.collection("usernames").doc(username);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(usernameRef);
    if (!snap.exists) return;

    if (snap.data().uid !== uid) {
      throw new functions.https.HttpsError("permission-denied");
    }

    tx.delete(usernameRef);
  });

  return { ok: true };
});


// =====================================================
// 7) uploadPermit() — NEW (Cloudinary Upload)
// =====================================================
// Admin sends Base64 → Cloudinary → store permitUrl to /clearances/{uid}
exports.uploadPermit = functions.https.onCall(async (data, context) => {
  if (!context.auth)
    throw new functions.https.HttpsError("unauthenticated");

  const uid = context.auth.uid;

  // Verify admin
  const userDoc = await db.collection("users").doc(uid).get();
  if (!userDoc.exists || userDoc.data().role !== "admin") {
    throw new functions.https.HttpsError("permission-denied", "Admins only");
  }

  const targetUid = data.targetUid;
  const base64Image = data.base64Image;

  if (!targetUid || !base64Image) {
    throw new functions.https.HttpsError("invalid-argument", "Missing data");
  }

  try {
    // Upload to Cloudinary
    const uploadRes = await cloudinary.uploader.upload(base64Image, {
      folder: "permits"
    });

    const permitUrl = uploadRes.secure_url;

    // Save to Firestore
    await db.collection("clearances").doc(targetUid).set(
      {
        permitUrl,
        permitReady: true,
        permitUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
      },
      { merge: true }
    );

    return { ok: true, permitUrl };
  } catch (err) {
    console.error("Cloudinary upload error:", err);
    throw new functions.https.HttpsError("internal", "Upload failed");
  }
});