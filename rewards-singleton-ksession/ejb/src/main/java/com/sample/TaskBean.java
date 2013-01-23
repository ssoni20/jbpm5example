package com.sample;

import java.util.List;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.persistence.OptimisticLockException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.UserTransaction;

import org.jbpm.task.TaskService;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.PermissionDeniedException;

@Stateless
@TransactionManagement(TransactionManagementType.BEAN)
public class TaskBean implements TaskLocal {

    @Resource
    private UserTransaction ut;

    public List<TaskSummary> retrieveTaskList(String actorId) throws Exception {

        TaskService localTaskService = JbpmUtil.getLocalTaskService();
        List<TaskSummary> list = null;
        synchronized (localTaskService) {
            list = localTaskService.getTasksAssignedAsPotentialOwner(actorId, "en-UK");

            System.out.println("retrieveTaskList by " + actorId);
            for (TaskSummary task : list) {
                System.out.println(" task.getId() = " + task.getId());
            }
        }

        return list;
    }

    public void approveTask(String actorId, long taskId) throws Exception {

        TaskService localTaskService = JbpmUtil.getLocalTaskService();

        synchronized (localTaskService) {

            try {
                ut.begin();

                System.out.println("approveTask (taskId = " + taskId + ") by " + actorId);
                localTaskService.start(taskId, actorId);
                localTaskService.complete(taskId, actorId, null);

                // Thread.sleep(10000); // To test OptimisticLockException

                ut.commit();
            } catch (RollbackException e) {
                e.printStackTrace();
                Throwable cause = e.getCause();
                if (cause != null && cause instanceof OptimisticLockException) {
                    // Concurrent access to the same process instance
                    throw new ProcessOperationException(
                            "The same process instance has likely been accessed concurrently", e);
                }
                throw new RuntimeException(e);
            } catch (PermissionDeniedException e) {
                e.printStackTrace();
                // Transaction might be already rolled back by
                // TaskServiceSession
                if (ut.getStatus() == Status.STATUS_ACTIVE) {
                    ut.rollback();
                }
                // Probably the task has already been started by other users
                throw new ProcessOperationException("The task (id = " + taskId
                        + ") has likely been started by other users ", e);
            } catch (Exception e) {
                e.printStackTrace();
                // Transaction might be already rolled back by
                // TaskServiceSession
                if (ut.getStatus() == Status.STATUS_ACTIVE) {
                    ut.rollback();
                }
                throw new RuntimeException(e);
            } finally {
                // don't dispose
            }
        }

        return;
    }
}
