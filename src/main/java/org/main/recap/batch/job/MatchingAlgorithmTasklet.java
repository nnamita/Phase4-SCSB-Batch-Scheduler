package org.main.recap.batch.job;

import org.apache.camel.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.main.recap.RecapConstants;
import org.main.recap.batch.service.UpdateJobDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by rajeshbabuk on 3/4/17.
 */
public class MatchingAlgorithmTasklet implements Tasklet{

    private static final Logger logger = LoggerFactory.getLogger(MatchingAlgorithmTasklet.class);

    @Value("${scsb.solr.client.url}")
    private String solrClientUrl;

    @Autowired
    private UpdateJobDetailsService updateJobDetailsService;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;


    /**
     * This method starts the execution of the matching algorithm job.
     * @param contribution
     * @param chunkContext
     * @return
     * @throws Exception
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        logger.info("Executing MatchingAlgorithmTasklet");
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        JobExecution jobExecution = stepExecution.getJobExecution();
        ExecutionContext executionContext = jobExecution.getExecutionContext();
        try {
            String fromDate = jobExecution.getJobParameters().getString(RecapConstants.FROM_DATE);
            Date createdDate;
            if (StringUtils.isNotBlank(fromDate)) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat(RecapConstants.FROM_DATE_FORMAT);
                createdDate = dateFormatter.parse(fromDate);
            } else {
                createdDate = jobExecution.getCreateTime();
            }
            long jobInstanceId = jobExecution.getJobInstance().getInstanceId();
            String jobName = jobExecution.getJobInstance().getJobName();
            String jobNameParam = (String) jobExecution.getExecutionContext().get(RecapConstants.JOB_NAME);
            logger.info("Job Parameter in Matching Algorithm Tasklet : {}", jobNameParam);
            if (!jobName.equalsIgnoreCase(jobNameParam)) {
                updateJobDetailsService.updateJob(solrClientUrl, jobName, jobExecution.getCreateTime(), jobInstanceId);
            }

            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(RecapConstants.JOB_ID, String.valueOf(jobExecution.getId()));
            requestMap.put(RecapConstants.PROCESS_TYPE, RecapConstants.ONGOING_MATCHING_ALGORITHM_JOB);
            requestMap.put(RecapConstants.CREATED_DATE, createdDate.toString());
            producerTemplate.sendBody(RecapConstants.MATCHING_ALGORITHM_JOB_INITIATE_QUEUE, requestMap);
            Endpoint endpoint = camelContext.getEndpoint(RecapConstants.MATCHING_ALGORITHM_JOB_COMPLETION_OUTGOING_QUEUE);
            PollingConsumer consumer = endpoint.createPollingConsumer();
            Exchange exchange = consumer.receive();
            String resultStatus = (String) exchange.getIn().getBody();
            if (StringUtils.isNotBlank(resultStatus)) {
                String[] resultSplitMessage = resultStatus.split("\\|");
                if (!resultSplitMessage[0].equalsIgnoreCase(RecapConstants.JOB_ID + ":" + jobExecution.getId())) {
                    producerTemplate.sendBody(RecapConstants.MATCHING_ALGORITHM_JOB_COMPLETION_OUTGOING_QUEUE, resultStatus);
                    resultStatus = RecapConstants.FAILURE + " - " + RecapConstants.FAILURE_QUEUE_MESSAGE;
                } else {
                    resultStatus = resultSplitMessage[1];
                }
            }
            logger.info("Job Id : {} Matching Algorithm Job Result Status : {}", jobExecution.getId(), resultStatus);

            if (!StringUtils.containsIgnoreCase(resultStatus, RecapConstants.SUCCESS)) {
                executionContext.put(RecapConstants.JOB_STATUS, RecapConstants.FAILURE);
                executionContext.put(RecapConstants.JOB_STATUS_MESSAGE, RecapConstants.MATCHING_ALGORITHM_STATUS_NAME + " " + resultStatus);
                stepExecution.setExitStatus(new ExitStatus(RecapConstants.FAILURE, RecapConstants.MATCHING_ALGORITHM_STATUS_NAME + " " + resultStatus));
            } else {
                executionContext.put(RecapConstants.JOB_STATUS, RecapConstants.SUCCESS);
                executionContext.put(RecapConstants.JOB_STATUS_MESSAGE, RecapConstants.MATCHING_ALGORITHM_STATUS_NAME + " " + resultStatus);
                stepExecution.setExitStatus(new ExitStatus(RecapConstants.SUCCESS, RecapConstants.MATCHING_ALGORITHM_STATUS_NAME + " " + resultStatus));
            }
        } catch (Exception ex) {
            logger.error(RecapConstants.LOG_ERROR, ExceptionUtils.getMessage(ex));
            executionContext.put(RecapConstants.JOB_STATUS, RecapConstants.FAILURE);
            executionContext.put(RecapConstants.JOB_STATUS_MESSAGE, RecapConstants.MATCHING_ALGORITHM_STATUS_NAME + " " + ExceptionUtils.getMessage(ex));
            stepExecution.setExitStatus(new ExitStatus(RecapConstants.FAILURE, ExceptionUtils.getFullStackTrace(ex)));
        }
        return RepeatStatus.FINISHED;
    }
}
