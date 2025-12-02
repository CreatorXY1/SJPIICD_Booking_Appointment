const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// When a user is created in Auth emulator, set a role based on email domain
exports.setCustomClaimOnSignup = functions.auth.user().onCreate(async (user) => {
  const email = user.email || '';
  console.log('New user created:', user.uid, email);
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
});

// Demo: when appointment doc status transitions to PAID, log and simulate email send
exports.onAppointmentPaid = functions.firestore
  .document('appointments/{appointmentId}')
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after = change.after.data() || {};
    if (before.status !== 'PAID' && after.status === 'PAID') {
      console.log(`Appointment ${context.params.appointmentId} marked PAID`);
      // Simulate an email send by logging (emulator cannot send real email without external API)
      console.log('Simulated email to:', after.userEmail, 'receiptUrl:', after.paymentReceiptUrl || 'none');
      // Optionally write a verification flag to appointment doc:
      await change.after.ref.update({ verifiedByFunction: true, verifiedAt: admin.firestore.FieldValue.serverTimestamp() });
    }
});
