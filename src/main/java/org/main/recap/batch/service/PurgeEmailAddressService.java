package org.main.recap.batch.service;

import org.main.recap.RecapConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Created by rajeshbabuk on 18/4/17.
 */

@Service
public class PurgeEmailAddressService {

    private static final Logger logger = LoggerFactory.getLogger(PurgeEmailAddressService.class);

    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    public String purgeEmailAddress(String serverProtocol, String scsbCircUrl) {
        String resultStatus = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(RecapConstants.API_KEY, RecapConstants.RECAP);
            HttpEntity httpEntity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = getRestTemplate().exchange(serverProtocol + scsbCircUrl + RecapConstants.PURGE_EMAIL_URL, HttpMethod.GET, httpEntity, String.class);
            resultStatus = responseEntity.getBody();
        } catch (Exception ex) {
            logger.error(RecapConstants.LOG_ERROR, ex);
            resultStatus = ex.getMessage();
        }
        return resultStatus;
    }
}