        package com.moutamid.letswander;

        import com.google.firebase.auth.FirebaseAuth;
        import com.google.firebase.database.DatabaseReference;
        import com.google.firebase.database.FirebaseDatabase;

        public class Constants {

            public static FirebaseAuth auth() {
                return FirebaseAuth.getInstance();
            }

            public static DatabaseReference databaseReference() {
                DatabaseReference db = FirebaseDatabase.getInstance().getReference().child("LetsWander");
                db.keepSynced(true);
                return db;
            }
        }
