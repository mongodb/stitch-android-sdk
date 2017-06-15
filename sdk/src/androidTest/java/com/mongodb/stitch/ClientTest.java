package com.mongodb.stitch;


import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.StitchClient;
import com.mongodb.stitch.android.auth.Auth;
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProvider;
import com.mongodb.stitch.android.test.BuildConfig;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Test for various {@link StitchClient} methods.
 * <p>
 * TODO: Add more test functions for the various calls.
 * TODO: Mock responses for requests to avoid sending them over the wire.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ClientTest {
    private Context instrumentationCtx;
    private SharedPreferences _preferences;
    private StitchClient stitchClient;

    private void assertThat(boolean assertion) {
        if (BuildConfig.DEBUG && !assertion) {
            throw new AssertionError("Failed assertion");
        }
    }

    @Before
    public void setup() {
        instrumentationCtx = InstrumentationRegistry.getContext();
        stitchClient = new StitchClient(
                instrumentationCtx,
                BuildConfig.TEST_APP,
                BuildConfig.BASE_URL
        );

        stitchClient.getProperties().clear();
        try {
            _preferences = (SharedPreferences) stitchClient.getClass()
                    .getDeclaredField("_preferences")
                    .get(stitchClient);
            _preferences.edit().clear().commit();
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRegister() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        stitchClient.register(
                String.format(
                        Locale.US,
                        "%d%s",
                        new Random().nextInt(Integer.MAX_VALUE),
                        "@baz.com"
                ), "foobar"
        ).addOnCompleteListener(new OnCompleteListener<Boolean>() {
            @Override
            public void onComplete(@NonNull Task<Boolean> task) {
                assertThat(task.getException() == null);
                assertThat(task.getResult());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void testLogin() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        stitchClient.logInWithProvider(
                new EmailPasswordAuthProvider("foo1@bar.com", "bazqux")
        ).addOnCompleteListener(new OnCompleteListener<Auth>() {
            @Override
            public void onComplete(@NonNull Task<Auth> task) {
                assertThat(task.getException() == null);
                Auth auth = task.getResult();
                assertThat(auth.getUser() != null);

                latch.countDown();
            }
        });

        latch.await();
    }
}
