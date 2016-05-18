/*
 * Original Guava code is copyright (C) 2015 The Guava Authors.
 * Modifications from Guava are copyright (C) 2016 DiffPlug.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.common.util.concurrent;

import static com.diffplug.common.util.concurrent.Futures.immediateFuture;
import static com.diffplug.common.util.concurrent.MoreExecutors.directExecutor;
import static com.diffplug.common.util.concurrent.Runnables.doNothing;
import static com.diffplug.common.util.concurrent.TestPlatform.verifyGetOnPendingFuture;
import static com.diffplug.common.util.concurrent.TestPlatform.verifyTimedGetOnPendingFuture;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import junit.framework.TestCase;

import com.diffplug.common.annotations.GwtCompatible;
import com.diffplug.common.util.concurrent.AbstractFuture.TrustedFuture;

/**
 * Base class for tests for emulated {@link AbstractFuture} that allow subclasses to swap in a
 * different "source Future" for {@link AbstractFuture#setFuture} calls.
 */
@GwtCompatible
abstract class AbstractAbstractFutureTest extends TestCase {
	private TestedFuture<Integer> future;
	private AbstractFuture<Integer> delegate;

	abstract AbstractFuture<Integer> newDelegate();

	@Override
	protected void setUp() {
		future = TestedFuture.create();
		delegate = newDelegate();
	}

	public void testPending() {
		assertPending(future);
	}

	public void testSuccessful() throws Exception {
		assertThat(future.set(1)).isTrue();
		assertSuccessful(future, 1);
	}

	public void testFailed() throws Exception {
		Exception cause = new Exception();
		assertThat(future.setException(cause)).isTrue();
		assertFailed(future, cause);
	}

	public void testCanceled() throws Exception {
		assertThat(future.cancel(false /* mayInterruptIfRunning */)).isTrue();
		assertCancelled(future, false);
	}

	public void testInterrupted() throws Exception {
		assertThat(future.cancel(true /* mayInterruptIfRunning */)).isTrue();
		assertCancelled(future, true);
	}

	public void testSetFuturePending() throws Exception {
		assertThat(future.setFuture(delegate)).isTrue();
		assertSetAsynchronously(future);
	}

	public void testSetFutureThenCancel() throws Exception {
		assertThat(future.setFuture(delegate)).isTrue();
		assertThat(future.cancel(false /* mayInterruptIfRunning */)).isTrue();
		assertCancelled(future, false);
		assertCancelled(delegate, false);
	}

	public void testSetFutureThenInterrupt() throws Exception {
		assertThat(future.setFuture(delegate)).isTrue();
		assertThat(future.cancel(true /* mayInterruptIfRunning */)).isTrue();
		assertCancelled(future, true);
		assertCancelled(delegate, true);
	}

	public void testSetFutureDelegateAlreadySuccessful() throws Exception {
		delegate.set(5);
		assertThat(future.setFuture(delegate)).isTrue();
		assertSuccessful(future, 5);
	}

	public void testSetFutureDelegateLaterSuccessful() throws Exception {
		assertThat(future.setFuture(delegate)).isTrue();
		delegate.set(6);
		assertSuccessful(future, 6);
	}

	public void testSetFutureDelegateAlreadyCancelled() throws Exception {
		delegate.cancel(false /** mayInterruptIfRunning */
		);
		assertThat(future.setFuture(delegate)).isTrue();
		assertCancelled(future, false);
	}

	public void testSetFutureDelegateLaterCancelled() throws Exception {
		assertThat(future.setFuture(delegate)).isTrue();
		delegate.cancel(false /** mayInterruptIfRunning */
		);
		assertCancelled(future, false);
	}

	public void testSetFutureDelegateAlreadyInterrupted() throws Exception {
		delegate.cancel(true /** mayInterruptIfRunning */
		);
		assertThat(future.setFuture(delegate)).isTrue();
		/*
		 * Interruption of the delegate propagates to us only if the delegate was a TrustedFuture.
		 * TODO(cpovirk): Consider whether to stop copying this information from TrustedFuture so that
		 * we're consistent.
		 */
		assertCancelled(future, delegate instanceof TrustedFuture);
	}

	public void testSetFutureDelegateLaterInterrupted() throws Exception {
		assertThat(future.setFuture(delegate)).isTrue();
		delegate.cancel(true /** mayInterruptIfRunning */
		);
		// See previous method doc.
		assertCancelled(future, delegate instanceof TrustedFuture);
	}

	public void testListenLaterSuccessful() {
		CountingRunnable listener = new CountingRunnable();

		future.addListener(listener, directExecutor());
		listener.assertNotRun();

		future.set(1);
		listener.assertRun();
	}

	public void testListenLaterFailed() {
		CountingRunnable listener = new CountingRunnable();

		future.addListener(listener, directExecutor());
		listener.assertNotRun();

		future.setException(new Exception());
		listener.assertRun();
	}

	public void testListenLaterCancelled() {
		CountingRunnable listener = new CountingRunnable();

		future.addListener(listener, directExecutor());
		listener.assertNotRun();

		future.cancel(false);
		listener.assertRun();
	}

	public void testListenLaterInterrupted() {
		CountingRunnable listener = new CountingRunnable();

		future.addListener(listener, directExecutor());
		listener.assertNotRun();

		future.cancel(true);
		listener.assertRun();
	}

	public void testListenLaterSetAsynchronously() {
		CountingRunnable listener = new CountingRunnable();

		future.addListener(listener, directExecutor());
		listener.assertNotRun();

		future.setFuture(delegate);
		listener.assertNotRun();
	}

	public void testListenLaterSetAsynchronouslyLaterDelegateSuccessful() {
		CountingRunnable before = new CountingRunnable();
		CountingRunnable inBetween = new CountingRunnable();
		CountingRunnable after = new CountingRunnable();

		future.addListener(before, directExecutor());
		future.setFuture(delegate);
		future.addListener(inBetween, directExecutor());
		delegate.set(1);
		future.addListener(after, directExecutor());

		before.assertRun();
		inBetween.assertRun();
		after.assertRun();
	}

	public void testListenLaterSetAsynchronouslyLaterDelegateFailed() {
		CountingRunnable before = new CountingRunnable();
		CountingRunnable inBetween = new CountingRunnable();
		CountingRunnable after = new CountingRunnable();

		future.addListener(before, directExecutor());
		future.setFuture(delegate);
		future.addListener(inBetween, directExecutor());
		delegate.setException(new Exception());
		future.addListener(after, directExecutor());

		before.assertRun();
		inBetween.assertRun();
		after.assertRun();
	}

	public void testListenLaterSetAsynchronouslyLaterDelegateCancelled() {
		CountingRunnable before = new CountingRunnable();
		CountingRunnable inBetween = new CountingRunnable();
		CountingRunnable after = new CountingRunnable();

		future.addListener(before, directExecutor());
		future.setFuture(delegate);
		future.addListener(inBetween, directExecutor());
		delegate.cancel(false);
		future.addListener(after, directExecutor());

		before.assertRun();
		inBetween.assertRun();
		after.assertRun();
	}

	public void testListenLaterSetAsynchronouslyLaterDelegateInterrupted() {
		CountingRunnable before = new CountingRunnable();
		CountingRunnable inBetween = new CountingRunnable();
		CountingRunnable after = new CountingRunnable();

		future.addListener(before, directExecutor());
		future.setFuture(delegate);
		future.addListener(inBetween, directExecutor());
		delegate.cancel(true);
		future.addListener(after, directExecutor());

		before.assertRun();
		inBetween.assertRun();
		after.assertRun();
	}

	public void testListenLaterSetAsynchronouslyLaterSelfCancelled() {
		CountingRunnable before = new CountingRunnable();
		CountingRunnable inBetween = new CountingRunnable();
		CountingRunnable after = new CountingRunnable();

		future.addListener(before, directExecutor());
		future.setFuture(delegate);
		future.addListener(inBetween, directExecutor());
		future.cancel(false);
		future.addListener(after, directExecutor());

		before.assertRun();
		inBetween.assertRun();
		after.assertRun();
	}

	public void testListenLaterSetAsynchronouslyLaterSelfInterrupted() {
		CountingRunnable before = new CountingRunnable();
		CountingRunnable inBetween = new CountingRunnable();
		CountingRunnable after = new CountingRunnable();

		future.addListener(before, directExecutor());
		future.setFuture(delegate);
		future.addListener(inBetween, directExecutor());
		future.cancel(true);
		future.addListener(after, directExecutor());

		before.assertRun();
		inBetween.assertRun();
		after.assertRun();
	}

	public void testMisbehavingListenerAlreadyDone() {
		class BadRunnableException extends RuntimeException {}

		Runnable bad = new Runnable() {
			@Override
			public void run() {
				throw new BadRunnableException();
			}
		};

		future.set(1);
		future.addListener(bad, directExecutor()); // BadRunnableException must not propagate.
	}

	public void testMisbehavingListenerLaterDone() {
		class BadRunnableException extends RuntimeException {}

		CountingRunnable before = new CountingRunnable();
		Runnable bad = new Runnable() {
			@Override
			public void run() {
				throw new BadRunnableException();
			}
		};
		CountingRunnable after = new CountingRunnable();

		future.addListener(before, directExecutor());
		future.addListener(bad, directExecutor());
		future.addListener(after, directExecutor());

		future.set(1); // BadRunnableException must not propagate.

		before.assertRun();
		after.assertRun();
	}

	public void testNullListener() {
		try {
			future.addListener(null, directExecutor());
			fail();
		} catch (NullPointerException expected) {}
	}

	public void testNullExecutor() {
		try {
			future.addListener(doNothing(), null);
			fail();
		} catch (NullPointerException expected) {}
	}

	public void testNullTimeUnit() throws Exception {
		future.set(1);
		try {
			future.get(0, null);
			fail();
		} catch (NullPointerException expected) {}
	}

	public void testSetNull() throws Exception {
		future.set(null);
		assertSuccessful(future, null);
	}

	public void testSetExceptionNull() throws Exception {
		try {
			future.setException(null);
			fail();
		} catch (NullPointerException expected) {}

		assertThat(future.isDone()).isFalse();
		assertThat(future.set(1)).isTrue();
		assertSuccessful(future, 1);
	}

	public void testSetFutureNull() throws Exception {
		try {
			future.setFuture(null);
			fail();
		} catch (NullPointerException expected) {}

		assertThat(future.isDone()).isFalse();
		assertThat(future.set(1)).isTrue();
		assertSuccessful(future, 1);
	}

	/**
	 * Concrete subclass for testing.
	 */
	private static class TestedFuture<V> extends AbstractFuture<V> {
		private static <V> TestedFuture<V> create() {
			return new TestedFuture<V>();
		}
	}

	private static final class CountingRunnable implements Runnable {
		int count;

		@Override
		public void run() {
			count++;
		}

		void assertNotRun() {
			assertEquals(0, count);
		}

		void assertRun() {
			assertEquals(1, count);
		}
	}

	private static void assertSetAsynchronously(AbstractFuture<Integer> future) {
		assertCannotSet(future);
		assertPending(future);
	}

	private static void assertPending(AbstractFuture<Integer> future) {
		assertThat(future.isDone()).isFalse();
		assertThat(future.isCancelled()).isFalse();

		CountingRunnable listener = new CountingRunnable();
		future.addListener(listener, directExecutor());
		listener.assertNotRun();

		verifyGetOnPendingFuture(future);
		verifyTimedGetOnPendingFuture(future);
	}

	private static void assertSuccessful(AbstractFuture<Integer> future, Integer expectedResult)
			throws InterruptedException, TimeoutException, ExecutionException {
		assertDone(future);
		assertThat(future.isCancelled()).isFalse();

		assertThat(future.get()).isEqualTo(expectedResult);
		assertThat(future.get(0, SECONDS)).isEqualTo(expectedResult);
		assertThat(future.get(-1, SECONDS)).isEqualTo(expectedResult);
	}

	private static void assertFailed(AbstractFuture<Integer> future, Throwable expectedException)
			throws InterruptedException, TimeoutException {
		assertDone(future);
		assertThat(future.isCancelled()).isFalse();

		try {
			future.get();
			fail();
		} catch (ExecutionException e) {
			assertThat(e.getCause()).isSameAs(expectedException);
		}

		try {
			future.get(0, SECONDS);
			fail();
		} catch (ExecutionException e) {
			assertThat(e.getCause()).isSameAs(expectedException);
		}
	}

	private static void assertCancelled(AbstractFuture<Integer> future, boolean expectWasInterrupted)
			throws InterruptedException, TimeoutException, ExecutionException {
		assertDone(future);
		assertThat(future.isCancelled()).isTrue();
		assertThat(future.wasInterrupted()).isEqualTo(expectWasInterrupted);

		try {
			future.get();
			fail();
		} catch (CancellationException expected) {}

		try {
			future.get(0, SECONDS);
			fail();
		} catch (CancellationException expected) {}
	}

	private static void assertDone(AbstractFuture<Integer> future) {
		CountingRunnable listener = new CountingRunnable();
		future.addListener(listener, directExecutor());
		listener.assertRun();

		assertThat(future.isDone()).isTrue();
		assertCannotSet(future);
		assertCannotCancel(future);
	}

	private static void assertCannotSet(AbstractFuture<Integer> future) {
		assertThat(future.set(99)).isFalse();
		assertThat(future.setException(new IndexOutOfBoundsException())).isFalse();
		assertThat(future.setFuture(new AbstractFuture<Integer>() {})).isFalse();
		assertThat(future.setFuture(immediateFuture(99))).isFalse();
	}

	private static void assertCannotCancel(AbstractFuture<Integer> future) {
		assertThat(future.cancel(true)).isFalse();
		assertThat(future.cancel(false)).isFalse();
	}
}