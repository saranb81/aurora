/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aurora.scheduler.updater;

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import com.twitter.common.testing.easymock.EasyMockTest;

import org.apache.aurora.gen.AssignedTask;
import org.apache.aurora.gen.Identity;
import org.apache.aurora.gen.InstanceKey;
import org.apache.aurora.gen.ScheduleStatus;
import org.apache.aurora.gen.ScheduledTask;
import org.apache.aurora.gen.TaskConfig;
import org.apache.aurora.scheduler.base.JobKeys;
import org.apache.aurora.scheduler.storage.entities.IInstanceKey;
import org.apache.aurora.scheduler.storage.entities.IJobKey;
import org.apache.aurora.scheduler.storage.entities.IScheduledTask;
import org.junit.Before;
import org.junit.Test;

import static org.apache.aurora.scheduler.events.PubsubEvent.SchedulerActive;
import static org.apache.aurora.scheduler.events.PubsubEvent.TaskStateChange;
import static org.apache.aurora.scheduler.events.PubsubEvent.TasksDeleted;
import static org.easymock.EasyMock.expectLastCall;

public class JobUpdateEventSubscriberTest extends EasyMockTest {

  private static final IJobKey JOB = JobKeys.from("role", "env", "name");

  private static final IScheduledTask TASK = IScheduledTask.build(
      new ScheduledTask()
          .setStatus(ScheduleStatus.PENDING)
          .setAssignedTask(
              new AssignedTask()
                  .setInstanceId(5)
                  .setTask(new TaskConfig()
                      .setJob(JOB.newBuilder())
                      .setOwner(new Identity().setRole(JOB.getRole()))
                      .setEnvironment(JOB.getEnvironment())
                      .setJobName(JOB.getName()))));
  private static final IInstanceKey INSTANCE_A = IInstanceKey.build(
      new InstanceKey()
          .setJobKey(JOB.newBuilder())
          .setInstanceId(TASK.getAssignedTask().getInstanceId()));

  private JobUpdateController updater;

  private EventBus eventBus;

  @Before
  public void setUp() {
    updater = createMock(JobUpdateController.class);

    eventBus = new EventBus();
    eventBus.register(new JobUpdateEventSubscriber(updater));
  }

  @Test
  public void testStateChange() throws Exception {
    updater.instanceChangedState(TASK);

    control.replay();

    eventBus.post(TaskStateChange.initialized(TASK));
  }

  @Test
  public void testDeleted() throws Exception {
    updater.instanceDeleted(INSTANCE_A);

    control.replay();

    eventBus.post(new TasksDeleted(ImmutableSet.of(TASK)));
  }

  @Test
  public void testSchedulerStartup() throws Exception {
    updater.systemResume();

    control.replay();

    eventBus.post(new SchedulerActive());
  }

  @Test
  public void testHandlesExceptions() throws Exception {
    updater.systemResume();
    expectLastCall().andThrow(new RuntimeException());
    updater.instanceChangedState(TASK);
    expectLastCall().andThrow(new RuntimeException());
    updater.instanceDeleted(INSTANCE_A);
    expectLastCall().andThrow(new RuntimeException());

    control.replay();

    eventBus.post(new SchedulerActive());
    eventBus.post(TaskStateChange.initialized(TASK));
    eventBus.post(new TasksDeleted(ImmutableSet.of(TASK)));
  }

  @Test
  public void testIgnoresPrunedTasks() throws Exception {
    control.replay();

    IScheduledTask task =
        IScheduledTask.build(TASK.newBuilder().setStatus(ScheduleStatus.FAILED));
    eventBus.post(new TasksDeleted(ImmutableSet.of(task)));
  }
}
