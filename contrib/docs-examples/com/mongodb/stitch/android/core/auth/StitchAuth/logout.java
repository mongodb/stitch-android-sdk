StitchAppClient client = Stitch.getDefaultAppClient();
StitchAuth auth = client.getAuth();
auth.logout().addOnCompleteListener(new OnCompleteListener<Void>() {
    @Override
    public void onComplete(@NonNull Task<Void> task) {
        if (task.isSuccessful()) {
            Log.i(TAG, "Successfully logged out!");
        } else {
            Log.e(TAG, "Logout failed!", task.getException());
        }
    }
});
