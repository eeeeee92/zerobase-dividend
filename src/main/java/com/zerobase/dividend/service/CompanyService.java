package com.zerobase.dividend.service;

import com.zerobase.dividend.exception.impl.NoCompanyException;
import com.zerobase.dividend.model.Company;
import com.zerobase.dividend.model.ScrapedResult;
import com.zerobase.dividend.persist.CompanyRepository;
import com.zerobase.dividend.persist.DividendRepository;
import com.zerobase.dividend.persist.entity.CompanyEntity;
import com.zerobase.dividend.persist.entity.DividendEntity;
import com.zerobase.dividend.scraper.Scraper;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.Trie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class CompanyService {

    private final Trie trie;
    private final Scraper yahooFinanceScraper;

    private final CompanyRepository companyRepository;
    private final DividendRepository dividendRepository;

    public Company save(String ticker) {
        boolean exists = this.companyRepository.existsByTicker(ticker);
        if(exists){
            throw new RuntimeException("already exists ticker ->" + ticker);
        }
        return this.storeCompanyAndDividend(ticker);
    }

    public Page<CompanyEntity> getAllCompany(Pageable pageable) {
        return companyRepository.findAll(pageable);

    }

    public void addAutoCompleteKeyword(String keyword){
        trie.put(keyword, null);
    }

    public List<String> autocomplete(String keyword){
        return (List<String>) trie.prefixMap(keyword).keySet().stream()
                .limit(10)
                .collect(Collectors.toList());
    }

    public void deleteAutocompleteKeyword(String keyword){
        trie.remove(keyword);
    }
    private Company storeCompanyAndDividend(String ticker){
        //ticker 를 기준으로 회사를 스크래핑
        Company company = this.yahooFinanceScraper.scrapCompanyByTicker(ticker);
        if(ObjectUtils.isEmpty(company)){
            throw new RuntimeException("failed to scrap ticker -> " + ticker);
        }

        // 해당 회사가 존재할 경우, 회사의 배당금 정보를 스크래핑
        ScrapedResult scrapedResult = this.yahooFinanceScraper.scrap(company);

        //스크래핑 결과
        CompanyEntity companyEntity = this.companyRepository.save(new CompanyEntity(company));

        List<DividendEntity> dividendEntities = scrapedResult.getDividends()
                .stream().map(e -> new DividendEntity(companyEntity.getId(), e))
                .collect(Collectors.toList());
        this.dividendRepository.saveAll(dividendEntities);
        return company;
    }

    public String deleteCompany(String ticker) {
        CompanyEntity company = companyRepository.findByTicker(ticker)
                .orElseThrow(NoCompanyException::new);

        dividendRepository.deleteAllByCompanyId(company.getId());
        companyRepository.delete(company);
        deleteAutocompleteKeyword(company.getName());
        return company.getName();
    }
}
