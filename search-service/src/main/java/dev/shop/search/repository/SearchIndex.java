package dev.shop.search.repository;
import com.fasterxml.jackson.databind.*;
import dev.shop.search.dto.SearchDtos.*;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.*;
@Repository
public class SearchIndex {
    private final RestClient client; private final ObjectMapper mapper;
    public SearchIndex(@Value("${commerce.elasticsearch-url}") String url,ObjectMapper mapper) {
        this.mapper=mapper;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(5));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public void ensureIndex() {
        try { client.head().uri("/products").retrieve().toBodilessEntity(); return; }
        catch(HttpClientErrorException.NotFound ignored) { }
        var properties=Map.of("id",Map.of("type","keyword"),"sku",Map.of("type","keyword"),"name",Map.of("type","text"),"description",Map.of("type","text"),"category",Map.of("type","keyword"),"price",Map.of("type","scaled_float","scaling_factor",100),"active",Map.of("type","boolean"),"version",Map.of("type","long"),"createdAt",Map.of("type","date"),"updatedAt",Map.of("type","date"));
        try { client.put().uri("/products").contentType(MediaType.APPLICATION_JSON).body(Map.of("settings",Map.of("number_of_shards",3,"number_of_replicas",0),"mappings",Map.of("dynamic","strict","properties",properties))).retrieve().toBodilessEntity(); }
        catch(HttpClientErrorException.BadRequest ex) { if(!ex.getResponseBodyAsString().contains("resource_already_exists_exception")) throw ex; }
    }
    public void project(UUID id,JsonNode product) {
        long version=product.path("version").asLong();
        if(version<1 || !id.toString().equals(product.path("id").asText())) throw new IllegalArgumentException("Invalid product projection");
        ensureIndex();
        try { client.put().uri("/products/_doc/{id}?version={version}&version_type=external_gte",id,version).contentType(MediaType.APPLICATION_JSON).body(product).retrieve().toBodilessEntity(); }
        catch(HttpClientErrorException.Conflict ignored) { /* Older revisions cannot overwrite a newer product or tombstone. */ }
    }
    public Page search(String query,String category,BigDecimal min,BigDecimal max,int page,int size) {
        ensureIndex();
        List<Object> filters=new ArrayList<>(); filters.add(Map.of("term",Map.of("active",true)));
        if(category!=null) filters.add(Map.of("term",Map.of("category",category)));
        Map<String,Object> range=new HashMap<>(); if(min!=null) range.put("gte",min); if(max!=null) range.put("lte",max);
        if(!range.isEmpty()) filters.add(Map.of("range",Map.of("price",range)));
        Object must=query==null||query.isBlank()?Map.of("match_all",Map.of()):Map.of("multi_match",Map.of("query",query,"fields",List.of("name^3","description","sku")));
        var body=Map.of("from",page*size,"size",size,"track_total_hits",true,"query",Map.of("bool",Map.of("must",must,"filter",filters)),"sort",List.of(Map.of("_score","desc"),Map.of("id","asc")));
        JsonNode result=client.post().uri("/products/_search").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        if(result==null) throw new IllegalStateException("Empty search result");
        List<Hit> hits=new ArrayList<>();
        for(var hit:result.path("hits").path("hits")) hits.add(mapper.convertValue(hit.path("_source"),Hit.class));
        return new Page(hits,result.path("hits").path("total").path("value").asLong(),page,size);
    }
}
