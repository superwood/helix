/**
 * Copyright (C) 2012 LinkedIn Inc <opensource@linkedin.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.helix.webapp.resources;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

import com.linkedin.helix.DataAccessor;
import com.linkedin.helix.HelixException;
import com.linkedin.helix.PropertyKey;
import com.linkedin.helix.PropertyKey.Builder;
import com.linkedin.helix.PropertyType;
import com.linkedin.helix.ZNRecord;
import com.linkedin.helix.tools.ClusterSetup;
import com.linkedin.helix.webapp.RestAdminApplication;

public class IdealStateResource extends Resource
{
  public static final String _replicas = "replicas";

  public IdealStateResource(Context context, Request request, Response response)
  {
    super(context, request, response);
    getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    getVariants().add(new Variant(MediaType.APPLICATION_JSON));
  }

  @Override
  public boolean allowGet()
  {
    return true;
  }

  @Override
  public boolean allowPost()
  {
    return true;
  }

  @Override
  public boolean allowPut()
  {
    return false;
  }

  @Override
  public boolean allowDelete()
  {
    return false;
  }

  @Override
  public Representation represent(Variant variant)
  {
    StringRepresentation presentation = null;
    try
    {
      String zkServer =
          (String) getContext().getAttributes().get(RestAdminApplication.ZKSERVERADDRESS);
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      String resourceName = (String) getRequest().getAttributes().get("resourceName");
      presentation = getIdealStateRepresentation(zkServer, clusterName, resourceName);
    }

    catch (Exception e)
    {
      String error = ClusterRepresentationUtil.getErrorAsJsonStringFromException(e);
      presentation = new StringRepresentation(error, MediaType.APPLICATION_JSON);

      e.printStackTrace();
    }
    return presentation;
  }

  StringRepresentation getIdealStateRepresentation(String zkServerAddress,
                                                   String clusterName,
                                                   String resourceName) throws JsonGenerationException,
      JsonMappingException,
      IOException
  {
    Builder keyBuilder = new PropertyKey.Builder(clusterName);

    String message =
        ClusterRepresentationUtil.getClusterPropertyAsString(zkServerAddress,
                                                             clusterName,
                                                             keyBuilder.idealStates(resourceName),
                                                             MediaType.APPLICATION_JSON);

    StringRepresentation representation =
        new StringRepresentation(message, MediaType.APPLICATION_JSON);

    return representation;
  }

  @Override
  public void acceptRepresentation(Representation entity)
  {
    try
    {
      String zkServer =
          (String) getContext().getAttributes().get(RestAdminApplication.ZKSERVERADDRESS);
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      String resourceName = (String) getRequest().getAttributes().get("resourceName");

      Form form = new Form(entity);

      Map<String, String> paraMap = ClusterRepresentationUtil.getFormJsonParameters(form);

      if (paraMap.get(ClusterRepresentationUtil._managementCommand)
                 .equalsIgnoreCase(ClusterRepresentationUtil._alterIdealStateCommand))
      {
        String newIdealStateString =
            form.getFirstValue(ClusterRepresentationUtil._newIdealState, true);

        ObjectMapper mapper = new ObjectMapper();
        ZNRecord newIdealState =
            mapper.readValue(new StringReader(newIdealStateString), ZNRecord.class);

        DataAccessor accessor =
            ClusterRepresentationUtil.getClusterDataAccessor(zkServer, clusterName);
        accessor.removeProperty(PropertyType.IDEALSTATES, resourceName);

        accessor.setProperty(PropertyType.IDEALSTATES, newIdealState, resourceName);

      }
      else if (paraMap.get(ClusterRepresentationUtil._managementCommand)
                      .equalsIgnoreCase(ClusterRepresentationUtil._rebalanceCommand))
      {
        int replicas = Integer.parseInt(paraMap.get(_replicas));
        ClusterSetup setupTool = new ClusterSetup(zkServer);
        setupTool.rebalanceStorageCluster(clusterName, resourceName, replicas);
      }
      else
      {
        new HelixException("Missing '"
            + ClusterRepresentationUtil._alterIdealStateCommand + "' or '"
            + ClusterRepresentationUtil._rebalanceCommand + "' command");
      }
      getResponse().setEntity(getIdealStateRepresentation(zkServer,
                                                          clusterName,
                                                          resourceName));
      getResponse().setStatus(Status.SUCCESS_OK);
    }

    catch (Exception e)
    {
      getResponse().setEntity(ClusterRepresentationUtil.getErrorAsJsonStringFromException(e),
                              MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
    }
  }
}
