public void loginAnonymously() {
    StitchAppClient client = Stitch.getDefaultAppClient();
    StitchAuth auth = client.getAuth();

    // The Stitch app is configured for Anonymous login in the Stitch UI
    auth.loginWithCredential(new AnonymousCredential()).addOnCompleteListener(new OnCompleteListener<StitchUser>() {
        @Override
        public void onComplete(@NonNull Task<StitchUser> task) {
           if (task.isSuccessful()) {
               Log.i(TAG, "Anonymous login successful!");
           } else {
               Log.e(TAG, "Anonymous login failed!", task.getException());
           }
        }
    });
}
