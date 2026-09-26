package com.mannequin.client.config;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ClientPreferencesTest {

    @Test
    public void testConcurrentUpdatesAndMemoryIntegrity() throws InterruptedException {
        ClientPreferences prefs = ClientPreferences.INSTANCE;
        int threads = 10;
        int iterations = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        final int count = i;
                        prefs.updateRoot(tag -> {
                            tag.putInt("thread_" + threadId, count);
                        });
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        pool.shutdown();
        Assertions.assertTrue(finished, "Concurrent preference updates timed out");

        CompoundTag snapshot = prefs.getRoot();
        for (int t = 0; t < threads; t++) {
            Assertions.assertTrue(snapshot.contains("thread_" + t), "Missing key for thread_" + t);
            Assertions.assertEquals(iterations - 1, snapshot.getInt("thread_" + t));
        }

        // Test section update
        prefs.updateSection("TestSection", sec -> {
            sec.putString("name", "director");
            sec.putDouble("fps", 60.0);
        });

        CompoundTag sec = prefs.getSection("TestSection");
        Assertions.assertEquals("director", sec.getString("name"));
        Assertions.assertEquals(60.0, sec.getDouble("fps"));
    }
}
