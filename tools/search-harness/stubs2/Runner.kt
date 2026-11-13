package androidx.test.ext.junit.runners

/** Harness stand-in for the AndroidX runner (Robolectric's on CI): plain JUnit 4 here. */
class AndroidJUnit4(klass: Class<*>) : org.junit.runners.BlockJUnit4ClassRunner(klass)
