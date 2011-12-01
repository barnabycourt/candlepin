/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.pinsetter.tasks;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.model.JobCurator;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;
import org.fedoraproject.candlepin.pinsetter.core.TestJob;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.ArrayList;
import java.util.List;

/**
 * RefreshPoolsJobListenerTest
 */
public class RefreshPoolsJobListenerTest {

    private RefreshPoolsJobListener listener;
    private JobCurator jcurator;
    private JobExecutionContext ctx;
    private JobExecutionException exc;
    private JobDetail detail;
    private Scheduler scheduler;

    @Before
    public void init() {
        jcurator = mock(JobCurator.class);
        listener = new RefreshPoolsJobListener(jcurator);
        ctx = mock(JobExecutionContext.class);
        exc = mock(JobExecutionException.class);
        detail = mock(JobDetail.class);
        scheduler = mock(Scheduler.class);
        JobDataMap map = new JobDataMap();

        when(detail.getJobDataMap()).thenReturn(map);
        when(ctx.getJobDetail()).thenReturn(detail);
        when(ctx.getScheduler()).thenReturn(scheduler);
        when(detail.getJobClass()).thenReturn(RefreshPoolsJob.class);
    }

    @Test
    public void name() {
        assertEquals("refresh jobs listener", listener.getName());
    }

    @Test
    public void toBeExecuted() {
        listener.jobToBeExecuted(ctx);
        verifyZeroInteractions(ctx);
    }

    @Test
    public void vetoed() {
        listener.jobExecutionVetoed(ctx);
        verifyZeroInteractions(ctx);
    }

    @Test
    public void wasExecuted() throws SchedulerException {
        detail.getJobDataMap().put(JobStatus.TARGET_ID, "admin");
        List<JobStatus> statuses = new ArrayList<JobStatus>();
        JobStatus status = mock(JobStatus.class);
        when(status.getId()).thenReturn("refresh_pools_33dbb42ffc24");
        statuses.add(status);

        when(scheduler.getJobDetail(eq("refresh_pools_33dbb42ffc24"),
            eq(PinsetterKernel.SINGLE_JOB_GROUP))).thenReturn(detail);
        when(detail.getName()).thenReturn("refresh_pools_33dbb42ffc24");
        when(jcurator.findPendingByOwnerKeyAndName(eq("admin"),
            eq("refresh_pools"))).thenReturn(statuses);

        listener.jobWasExecuted(ctx, exc);

        verify(scheduler, atLeastOnce()).resumeJob(
            eq("refresh_pools_33dbb42ffc24"), eq(PinsetterKernel.SINGLE_JOB_GROUP));
        verifyZeroInteractions(exc);
    }

    @Test
    public void wasNotExecuted() {
        when(detail.getJobClass()).thenReturn(TestJob.class);
        listener.jobWasExecuted(ctx, exc);
        verifyZeroInteractions(exc);
        verify(jcurator, never()).findPendingByOwnerKeyAndName(anyString(), anyString());
    }

    @Test
    public void nullStatuses() throws SchedulerException {
        detail.getJobDataMap().put(JobStatus.TARGET_ID, "admin");
        when(jcurator.findPendingByOwnerKeyAndName(eq("admin"),
            eq("refresh_pools"))).thenReturn(null);

        listener.jobWasExecuted(ctx, exc);

        verifyZeroInteractions(exc);
        verify(scheduler, never()).resumeJob(anyString(), anyString());
    }

    @Test
    public void emptyStatus() throws SchedulerException {
        detail.getJobDataMap().put(JobStatus.TARGET_ID, "admin");
        when(jcurator.findPendingByOwnerKeyAndName(eq("admin"),
            eq("refresh_pools"))).thenReturn(new ArrayList<JobStatus>());

        listener.jobWasExecuted(ctx, exc);

        verifyZeroInteractions(exc);
        verify(scheduler, never()).resumeJob(anyString(), anyString());
    }

    @Test
    public void skipNullDetails() throws SchedulerException {
        // prep to make sure we log a message upon error
        Logger log = Logger.getLogger(RefreshPoolsJobListener.class);
        Appender mockapp = mock(Appender.class);
        log.addAppender(mockapp);
        log.setLevel(Level.DEBUG);
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);

        detail.getJobDataMap().put(JobStatus.TARGET_ID, "admin");
        List<JobStatus> statuses = new ArrayList<JobStatus>();
        JobStatus status = mock(JobStatus.class);
        statuses.add(status);
        JobStatus status1 = mock(JobStatus.class);
        statuses.add(status1);

        when(status.getId()).thenReturn("refresh_pools_33dbb42ffc24");
        when(status1.getId()).thenReturn("refresh_pools_33dbb42ffc25");
        when(scheduler.getJobDetail(eq("refresh_pools_33dbb42ffc25"),
            eq(PinsetterKernel.SINGLE_JOB_GROUP))).thenReturn(null);
        when(scheduler.getJobDetail(eq("refresh_pools_33dbb42ffc24"),
            eq(PinsetterKernel.SINGLE_JOB_GROUP))).thenReturn(detail);
        when(jcurator.findPendingByOwnerKeyAndName(eq("admin"),
            eq("refresh_pools"))).thenReturn(statuses);

        listener.jobWasExecuted(ctx, exc);

        verify(mockapp, atMost(1)).doAppend(message.capture());
        verify(scheduler, atMost(1)).resumeJob(anyString(), anyString());
        verifyZeroInteractions(exc);
    }
}
