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

import junit.framework.TestCase;

import com.diffplug.common.collect.ForwardingObject;

/**
 * Tests for {@link ForwardingObjectTester}.
 *
 * @author Ben Yu
 */
public class ForwardingObjectTesterTest extends TestCase {

	public void testFailsToForward() {
		try {
			ForwardingObjectTester.testForwardingObject(FailToForward.class);
		} catch (AssertionError expected) {
			return;
		} catch (UnsupportedOperationException expectedOnAndroid) {
			return;
		}
		fail("Should have thrown");
	}

	@SuppressUnderAndroid // TODO(cpovirk): java.lang.IllegalAccessError: superclass not accessible
	public void testSuccessfulForwarding() {
		ForwardingObjectTester.testForwardingObject(ForwardToDelegate.class);
	}

	private static abstract class FailToForward extends ForwardingObject implements Runnable {
		@Override
		public void run() {}
	}

	private static abstract class ForwardToDelegate extends ForwardingObject implements Runnable {
		@Override
		public void run() {
			delegate().run();
		}

		@Override
		protected abstract Runnable delegate();
	}
}