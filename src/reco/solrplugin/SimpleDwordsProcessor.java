package reco.solrplugin;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.BasicNameValuePair;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vignesh on 21/01/18.
 */
public class SimpleDwordsProcessor extends UpdateRequestProcessor {
    public SimpleDwordsProcessor(UpdateRequestProcessor next) {
        super(next);
    }

    //@Override
    public void processCountAdd(AddUpdateCommand cmd) throws IOException {
        String title = (String) cmd.getSolrInputDocument().get("clue").getValue();
        cmd.getSolrInputDocument().addField("clue_tag_i", title.split(" ").length);
        super.processAdd(cmd);
    }

    @Override
    public void processAdd(AddUpdateCommand cmd) throws IOException {

        String input = cmd.getLuceneDocument().getField("clue").stringValue();
        String error = "";

        String url = "http://localhost:8983/solr/dwords";
        HttpSolrClient client = null;
        HttpPost post;
        String responseBody = null;
        String tokens = "";

        try {


            client = new HttpSolrClient.Builder(url).build();

            HttpClient httpClient = client.getHttpClient();

            //Params
            List<NameValuePair> postParameters = new ArrayList<>();
            postParameters.add(new BasicNameValuePair("fq", "type:B"));

            //URI builder
            URIBuilder uriBuilder = new URIBuilder(client.getBaseURL() + "/tag");
            uriBuilder.addParameters(postParameters);

            post = new HttpPost(uriBuilder.build());

            post.setHeader("Content-Type", "application/json");
            post.setEntity(new InputStreamEntity(new ByteArrayInputStream(input.getBytes("UTF-8")), -1));
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            responseBody = httpClient.execute(post, responseHandler);

            this.setBrandname(cmd, responseBody);

            //Set model
            String model = cmd.getSolrInputDocument().getFieldValue("clue").toString();
            cmd.getSolrInputDocument().addField("tokens", model);

        }
        catch (Exception sse) {
            error = sse.getMessage();
            cmd.getSolrInputDocument().addField("tokens_err",  error.toString() +
                    tokens);
        }



        super.processAdd(cmd);
    }

    private void setIDs(AddUpdateCommand cmd, String responseBody) throws ParseException {

        JSONObject response = null;
        JSONParser parser = new JSONParser();
        response = (JSONObject) parser.parse(responseBody);

        JSONArray tags = (JSONArray) response.get("tags");
        JSONArray tagIds = (JSONArray) tags.get(0);

        boolean idsKey = false;

        for(Object o: tagIds){
            if(o instanceof String) {

                String key = (String) o;
                idsKey = key.equals("ids");
            }
            if ( (o instanceof JSONArray) && idsKey ) {

                JSONArray ids = (JSONArray) o;
                cmd.getSolrInputDocument().addField("tokens", ids);
            }
        }
    }

    private void setBrandname(AddUpdateCommand cmd, String responseBody) throws ParseException {

        JSONObject response = null;
        JSONParser parser = new JSONParser();
        response = (JSONObject) parser.parse(responseBody);

        JSONObject responseNode = (JSONObject) response.get("response");
        JSONArray elements = (JSONArray) responseNode.get("docs");
        JSONObject element = (JSONObject) elements.get(0);
        String brandName = (String) element.get("phrase");
        cmd.getSolrInputDocument().addField("tokens", brandName);
    }
}
