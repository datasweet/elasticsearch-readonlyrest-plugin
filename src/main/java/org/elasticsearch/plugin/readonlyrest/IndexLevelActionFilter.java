/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */

package org.elasticsearch.plugin.readonlyrest;

import static org.elasticsearch.rest.RestStatus.FORBIDDEN;
import static org.elasticsearch.rest.RestStatus.NOT_FOUND;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.Block;
import org.elasticsearch.plugin.readonlyrest.security.UserTransient;
import org.elasticsearch.plugin.readonlyrest.utils.ThreadConstants;
import org.elasticsearch.plugin.readonlyrest.wiring.ThreadRepo;
import org.elasticsearch.plugin.readonlyrest.wiring.requestcontext.RequestContext;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;

/**
 * Created by sscarduzio on 19/12/2015.
 */
@Singleton
public class IndexLevelActionFilter extends AbstractComponent implements ActionFilter {
	private final ThreadPool threadPool;
	private final IndexNameExpressionResolver indexResolver;
	private ClusterService clusterService;
	private ConfigurationHelper conf;

	@Inject
	public IndexLevelActionFilter(Settings settings, ConfigurationHelper conf, ClusterService clusterService,
			ThreadPool threadPool, IndexNameExpressionResolver indexResolver) {
		super(settings);
		this.conf = conf;
		this.clusterService = clusterService;
		this.threadPool = threadPool;
		this.indexResolver = indexResolver;

		logger.info("Readonly REST plugin was loaded...");

		if (!conf.enabled) {
			logger.info("Readonly REST plugin is disabled!");
			return;
		}

		logger.info("Readonly REST plugin is enabled!");
	}

	@Override
	public int order() {
		return 0;
	}

	@Override
	public <Request extends ActionRequest, Response extends ActionResponse> void apply(Task task, String action,
			Request request, ActionListener<Response> listener, ActionFilterChain<Request, Response> chain) {
		// Skip if disabled
		RestChannel channel = ThreadRepo.channel.get();
		boolean chanNull = channel == null;

		RestRequest req = null;
		if (!chanNull) {
			req = channel.request();
		}
		boolean reqNull = req == null;

		// This was not a REST message
		if (reqNull && chanNull) {
			chain.proceed(task, action, request, listener);
			return;
		}

		// Bailing out in case of catastrophical misconfiguration that would
		// lead to insecurity
		if (reqNull != chanNull) {
			if (chanNull) {
				throw new SecurityPermissionException(
						"Problems analyzing the channel object. " + "Have you checked the security permissions?", null);
			}
			if (reqNull) {
				throw new SecurityPermissionException(
						"Problems analyzing the request object. " + "Have you checked the security permissions?", null);
			}
		}

		RequestContext rc = new RequestContext(channel, req, action, request, clusterService, indexResolver, threadPool);
		conf.acl.check(rc).exceptionally(throwable -> {
			logger.warn("forbidden request: " + rc + " Reason: " + throwable.getMessage());
			if (throwable.getCause() instanceof ResourceNotFoundException) {
				logger.warn("Resource not found! ID: " + rc.getId() + "  " + throwable.getCause().getMessage());
				sendNotFound((ResourceNotFoundException) throwable.getCause(), channel);
				return null;
			}
			throwable.printStackTrace();
			sendNotAuthResponse(channel);
			return null;
		})

				.thenApply(result -> {
					assert result != null;

					if (result.isMatch() && Block.Policy.ALLOW.equals(result.getBlock().getPolicy())) {
						try {
							String encodedUser = UserTransient.CreateFromRequestContext(rc).serialize();
							if (encodedUser == null) 
								logger.error("Error while serializing token");
							if (threadPool.getThreadContext().getHeader(ThreadConstants.userTransient) == null) {
								threadPool.getThreadContext().putHeader(ThreadConstants.userTransient, encodedUser);
							}

							if (request instanceof SearchRequest) {
								((SearchRequest)request).requestCache(Boolean.FALSE);
							} else if (request instanceof MultiSearchRequest) {
								for (SearchRequest sr : ((MultiSearchRequest)request).requests()) {
									sr.requestCache(Boolean.FALSE);
								}
							}

							@SuppressWarnings("unchecked")
							ActionListener<Response> aclActionListener = (ActionListener<Response>) new ACLActionListener(
									request, (ActionListener<ActionResponse>) listener, rc, result);

							chain.proceed(task, action, request, aclActionListener);
							return null;
						} catch (Throwable e) {
							e.printStackTrace();
						}

						chain.proceed(task, action, request, listener);
						return null;
					}

					logger.warn("forbidden request: " + rc + " Reason: " + result.getBlock() + " (" + result.getBlock()
							+ ")");
					sendNotAuthResponse(channel);
					return null;
				});
	}

	private void sendNotAuthResponse(RestChannel channel) {
		String reason = conf.forbiddenResponse;

		BytesRestResponse resp;
		resp = new BytesRestResponse(FORBIDDEN, BytesRestResponse.TEXT_CONTENT_TYPE, reason);

		channel.sendResponse(resp);
	}

	private void sendNotFound(ResourceNotFoundException e, RestChannel channel) {
		try {
			XContentBuilder b = JsonXContent.contentBuilder();
			b.startObject();
			ElasticsearchException.generateFailureXContent(b, ToXContent.EMPTY_PARAMS, e, true);
			b.endObject();
			BytesRestResponse resp;
			resp = new BytesRestResponse(NOT_FOUND, "application/json", b.string());
			channel.sendResponse(resp);
		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

}
