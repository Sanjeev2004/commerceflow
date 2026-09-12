package dev.shop.search.service;
import dev.shop.common.api.ApiException;
import dev.shop.search.dto.SearchDtos.Page;
import dev.shop.search.repository.SearchIndex;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
@Service
public class SearchService {
    private final SearchIndex index;
    public SearchService(SearchIndex index) { this.index=index; }
    public Page search(String query,String category,BigDecimal min,BigDecimal max,int page,int size) {
        if((long)page*size+size>10000 || (min!=null&&max!=null&&min.compareTo(max)>0)) throw new ApiException(HttpStatus.BAD_REQUEST,"Invalid price range or search window (maximum 10000)");
        try { return index.search(query,category,min,max,page,size); }
        catch(RestClientException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Search temporarily unavailable"); }
    }
}
