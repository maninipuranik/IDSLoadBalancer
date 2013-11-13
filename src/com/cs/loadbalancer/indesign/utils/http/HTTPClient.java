package com.cs.loadbalancer.indesign.utils.http;


import java.net.SocketTimeoutException;

import org.apache.http.HttpEntity;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.cs.loadbalancer.indesign.utils.http.exception.ConnectionException;
import com.cs.loadbalancer.indesign.utils.http.exception.ResponseTimeoutException;

public class HTTPClient {

	public String sendAndReceiveXML(String strURL, String requestXML) throws Throwable{

		CloseableHttpClient httpclient = HttpClients.createDefault();
		try {
			
			RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60 * 1000).build();
			HttpPost httppost = new HttpPost(strURL);
			httppost.setConfig(requestConfig);

			StringEntity reqEntity = new StringEntity(requestXML);
			reqEntity.setContentType("application/xml");
			httppost.setEntity(reqEntity);

			CloseableHttpResponse response = null;
			try {
				response = httpclient.execute(httppost);
			} 
			catch(ConnectTimeoutException e) {
				throw new ConnectionException(e);
			}
			catch (HttpHostConnectException e) {
				throw new ConnectionException(e);
			}
			catch(NoHttpResponseException e) {
				throw new ResponseTimeoutException(e);
			} 
			catch (SocketTimeoutException e) {
				throw new ResponseTimeoutException(e);
			}
			
			try {
				HttpEntity resEntity = response.getEntity();
				return EntityUtils.toString(resEntity);

			} finally {
				response.close();
			}
		} finally {
			httpclient.close();
		}
	}

}
