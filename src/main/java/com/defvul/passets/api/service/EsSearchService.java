package com.defvul.passets.api.service;

import com.defvul.passets.api.vo.ApplicationVO;
import com.google.common.base.Joiner;

import com.defvul.passets.api.bo.req.QueryBaseForm;
import com.defvul.passets.api.bo.res.*;
import com.defvul.passets.api.vo.Page;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 说明:
 * 时间: 2019/11/8 15:03
 *
 * @author wimas
 */
@Slf4j
@Service
public class EsSearchService {
    @Autowired
    private RestHighLevelClient client;

    @Value("${elasticsearch.index}")
    private String index;

    private static final String[] INCLUDE_SOURCE = new String[]{
            "code",
            "@timestamp",
            "pro",
            "type",
            "port",
            "host",
            "url",
            "url_tpl",
            "header",
            "site",
            "apps",
            "inner",
            "path",
            "title",
            "ip",
    };

    private static final String[] INCLUDE_SOURCE_ALL = new String[]{
            "code",
            "@timestamp",
            "pro",
            "type",
            "port",
            "host",
            "url",
            "url_tpl",
            "server",
            "header",
            "site",
            "apps",
            "inner",
            "path",
            "body",
            "title",
            "ip",
    };

    private static final String[] INCLUDE_SOURCE_IP = new String[]{
            "@timestamp",
            "pro",
            "port",
            "host",
            "url_tpl",
            "site",
            "apps",
            "inner",
            "ip",
    };

    private static final String[] INCLUDE_SOURCE_HOST_INFO = new String[]{
            "@timestamp",
            "inner",
            "pro",
            "site",
            "port",
            "apps",
            "title",
            "ip",
    };

    private static final String[] INCLUDE_SOURCE_HOST_INFO_ALL = new String[]{
            "@timestamp",
            "inner",
            "pro",
            "port",
            "apps",
            "title",
            "ip",
            "header",
            "body",
    };

    private static final String[] INCLUDE_SOURCE_HOST = new String[]{
            "ip",
            "inner",
            "apps.os",
            "geoip",
            "title",
    };

    private static final String[] INCLUDE_SOURCE_SITE = new String[]{
            "ip",
            "host",
            "inner",
            "site",
            "geoip",
            "title",
            "@timestamp",
    };

    private static final String[] INCLUDE_SOURCE_SITE_INFO = new String[]{
            "ip",
            "inner",
            "path",
            "site",
            "apps",
            "title",
    };

    private static final String[] INCLUDE_SOURCE_SITE_INFO_ALL = new String[]{
            "ip",
            "inner",
            "site",
            "apps",
            "title",
            "header",
            "host",
            "geoip",
            "body",
            "@timestamp",
    };

    private static final int SIZE = 2147483647;

    private static int total;

    @PostConstruct
    public void init() {
        ClusterUpdateSettingsRequest request = new ClusterUpdateSettingsRequest();
        request.persistentSettings(new HashMap<String, Object>(1) {{
            put("search.max_buckets", SIZE);
        }});
        try {
            client.cluster().putSettings(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("执行es设置报错: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * 查询一段时间内的IP和端口
     *
     * @param form
     * @return
     */
    public List<InfoBO> queryTimeSlotWithIpAndPort(QueryBaseForm form) {
        String termName = "ip_port";
        String topName = "top_score_hits";
        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        sourceBuilder.query(getBoolQueryWithQueryForm(form));

        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms(termName).field("host.keyword").size(SIZE);

        TopHitsAggregationBuilder topHitsAggregationBuilder = AggregationBuilders.topHits(topName).size(1).sort("@timestamp", SortOrder.DESC);
        topHitsAggregationBuilder.fetchSource(INCLUDE_SOURCE_IP, null);
        termsAggregationBuilder.subAggregation(topHitsAggregationBuilder);

        sourceBuilder.aggregation(termsAggregationBuilder);

        request.source(sourceBuilder);

        log.info("IP开始搜索");
        SearchResponse response = search(request);
        log.info("IP结束搜索");
        if (response == null) {
            return Collections.emptyList();
        }

        Terms terms = response.getAggregations().get(termName);
        List<InfoBO> result = new ArrayList<>();
        for (Terms.Bucket bucket : terms.getBuckets()) {
            ParsedTopHits hits = bucket.getAggregations().get(topName);
            String json = hits.getHits().getAt(0).getSourceAsString();
            InfoBO bo = new Gson().fromJson(json, InfoBO.class);
            bo.setCount(bucket.getDocCount());
            result.add(bo);
        }
        return result;
    }

    public Page<InfoBO> ipPage(QueryBaseForm form) {
        String termName = "ip_port";
        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        sourceBuilder.from(form.getCurrentPage() > 0 ? form.getCurrentPage() - 1 : form.getCurrentPage())
                .size(form.getPageSize())
                .sort("@timestamp", SortOrder.DESC);

        sourceBuilder.fetchSource(INCLUDE_SOURCE_IP, null).collapse(new CollapseBuilder("host.keyword"));
        sourceBuilder.query(QueryBuilders.termQuery("state", 1));
        sourceBuilder.query(getBoolQueryWithQueryForm(form));

        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms(termName).field("host.keyword").size(SIZE);
        termsAggregationBuilder.order(BucketOrder.aggregation("timestamp_order", false));

        MaxAggregationBuilder maxAggregationBuilder = AggregationBuilders.max("timestamp_order").field("@timestamp");
        termsAggregationBuilder.subAggregation(maxAggregationBuilder);

        sourceBuilder.aggregation(termsAggregationBuilder);

        request.source(sourceBuilder);

        SearchResponse response = search(request);
        if (response == null) {
            return new Page<>();
        }

        Terms terms = response.getAggregations().get(termName);
        int total = terms.getBuckets().size();

        Page<InfoBO> page = new Page<>();
        List<InfoBO> result = new ArrayList<>();
        page.setCurrentPage(form.getCurrentPage());
        page.setPageSize(form.getPageSize());
        page.setTotal(total);

        SearchHits searchHits = response.getHits();
        for (SearchHit hit : searchHits.getHits()) {
            String json = hit.getSourceAsString();
            InfoBO bo = new Gson().fromJson(json, InfoBO.class);
            terms.getBuckets().parallelStream().filter(b -> b.getKey().equals(bo.getHost())).forEach(b -> bo.setCount(b.getDocCount()));
            result.add(bo);
        }
        if (result.size() > 0) {
            page.setData(result.parallelStream().sorted(Comparator.comparing(InfoBO::getCount).reversed()).collect(Collectors.toList()));
        }

        return page;
    }

    public List<UrlBO> queryTimeSlotWithUrl(QueryBaseForm form) {
        String termName = "urls";
        String childTermName = "urls_child";
        String topName = "top_score_hits";
        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        sourceBuilder.query(getBoolQueryWithQueryForm(form));

        TermsAggregationBuilder aggregation = AggregationBuilders.terms(termName).field("site.keyword");
        aggregation.size(SIZE);

        TermsAggregationBuilder urlsChild = AggregationBuilders.terms(childTermName).field("url_tpl.keyword");
        urlsChild.size(SIZE);

        TopHitsAggregationBuilder topHitsAggregationBuilder = AggregationBuilders.topHits(topName).size(1).sort("@timestamp", SortOrder.DESC);
        topHitsAggregationBuilder.fetchSource(form.isFullField() ? INCLUDE_SOURCE_ALL : INCLUDE_SOURCE, null);
        urlsChild.subAggregation(topHitsAggregationBuilder);

        aggregation.subAggregation(urlsChild);

        sourceBuilder.aggregation(aggregation);

        request.source(sourceBuilder);
        SearchResponse response = search(request);
        if (response == null) {
            return Collections.emptyList();
        }

        Terms terms = response.getAggregations().get(termName);
        List<UrlBO> result = new ArrayList<>();
        for (Terms.Bucket bucket : terms.getBuckets()) {
            String key = bucket.getKeyAsString();
            long count = bucket.getDocCount();
            Terms termNode = bucket.getAggregations().get(childTermName);
            List<InfoBO> urls = new ArrayList<>();
            for (Terms.Bucket urlBucket : termNode.getBuckets()) {
                ParsedTopHits hits = urlBucket.getAggregations().get(topName);
                String json = hits.getHits().getAt(0).getSourceAsString();
                InfoBO bo = new Gson().fromJson(json, InfoBO.class);
                bo.setCount(urlBucket.getDocCount());
                urls.add(bo);
            }
            result.add(new UrlBO(key, count, urls));
        }
        return result;
    }

    public List<UrlBO> urlAll(QueryBaseForm form) {
        String termName = "urls";
        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        sourceBuilder.query(getBoolQueryWithQueryForm(form));

        TermsAggregationBuilder aggregation = AggregationBuilders.terms(termName).field("site.keyword");
        aggregation.size(SIZE).order(BucketOrder.aggregation("timestamp_order", false));

        MaxAggregationBuilder maxAggregationBuilder = AggregationBuilders.max("timestamp_order").field("@timestamp");

        aggregation.subAggregation(maxAggregationBuilder);
        sourceBuilder.aggregation(aggregation);

        request.source(sourceBuilder);
        SearchResponse response = search(request);
        if (response == null) {
            return Collections.emptyList();
        }

        Terms terms = response.getAggregations().get(termName);

        List<UrlBO> result = new ArrayList<>();
        for (Terms.Bucket bucket : terms.getBuckets()) {
            result.add(new UrlBO(bucket.getKeyAsString(), bucket.getDocCount(), null));
        }
        return result.parallelStream().sorted(Comparator.comparing(UrlBO::getCount).reversed()).collect(Collectors.toList());
    }

    public Page<InfoBO> urlsPage(QueryBaseForm form) {
        if (form.getUrl() == null) {
            return new Page<>();
        }
        String childTermName = "urls_child";
        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        sourceBuilder.from(form.getCurrentPage() > 0 ? form.getCurrentPage() - 1 : form.getCurrentPage())
                .size(form.getPageSize())
                .fetchSource(form.isFullField() ? INCLUDE_SOURCE_ALL : INCLUDE_SOURCE, null)
                .sort("@timestamp", SortOrder.DESC);
        BoolQueryBuilder boolQueryBuilder = getBoolQueryWithQueryForm(form);
        boolQueryBuilder.must(QueryBuilders.existsQuery("site"));
        sourceBuilder.query(boolQueryBuilder).collapse(new CollapseBuilder("url_tpl.keyword"));

        TermsAggregationBuilder urlsChild = AggregationBuilders.terms(childTermName).field("url_tpl.keyword");
        urlsChild.size(SIZE).order(BucketOrder.aggregation("timestamp_order", false));

        MaxAggregationBuilder maxAggregationBuilder = AggregationBuilders.max("timestamp_order").field("@timestamp");
        urlsChild.subAggregation(maxAggregationBuilder);

        sourceBuilder.aggregation(urlsChild);

        request.source(sourceBuilder);
        SearchResponse response = search(request);
        if (response == null) {
            return new Page<>();
        }
        Page<InfoBO> page = new Page<>();
        List<InfoBO> result = new ArrayList<>();

        SearchHits hits = response.getHits();
        Terms terms = response.getAggregations().get(childTermName);

        page.setCurrentPage(form.getCurrentPage());
        page.setPageSize(form.getPageSize());
        page.setTotal(terms.getBuckets().size());

        for (SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            InfoBO infoBO = new Gson().fromJson(json, InfoBO.class);
            terms.getBuckets().parallelStream()
                    .filter(b -> b.getKeyAsString().equals(form.getUrl())).forEach(b -> {
                infoBO.setCount(b.getDocCount());
                result.add(infoBO);
            });
        }
        if (result.size() > 0) {
            page.setData(result.parallelStream()
                    .sorted(Comparator.comparing(InfoBO::getTimestamp).reversed()).collect(Collectors.toList()));
        }
        return page;
    }


    public List<InfoBO> urlChild(QueryBaseForm form) {
        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();

        sourceBuilder.query(getBoolQueryWithQueryForm(form));
        String topName = "top_score_hits";
        String childTermName = "urls_child";
        TermsAggregationBuilder urlsChild = AggregationBuilders.terms(childTermName).field("url_tpl.keyword");
        urlsChild.size(SIZE);

        TopHitsAggregationBuilder topHitsAggregationBuilder = AggregationBuilders.topHits(topName).size(1).sort("@timestamp", SortOrder.DESC);
        topHitsAggregationBuilder.fetchSource(form.isFullField() ? INCLUDE_SOURCE_ALL : INCLUDE_SOURCE, null);
        urlsChild.subAggregation(topHitsAggregationBuilder);


        sourceBuilder.aggregation(urlsChild);

        request.source(sourceBuilder);
        SearchResponse response = search(request);
        if (response == null) {
            return Collections.emptyList();
        }

        Terms terms = response.getAggregations().get(childTermName);
        List<InfoBO> result = new ArrayList<>();
        for (Terms.Bucket bucket : terms.getBuckets()) {
            long count = bucket.getDocCount();
            ParsedTopHits hits = bucket.getAggregations().get(topName);
            String json = hits.getHits().getAt(0).getSourceAsString();
            InfoBO bo = new Gson().fromJson(json, InfoBO.class);
            bo.setCount(count);
            result.add(bo);
        }
        return result;

    }

    public Page<HostBO> host(QueryBaseForm form) {
        Page<HostBO> page = new Page<>();
        List<HostBO> hostBOList = queryHost(form, true);
        page.setTotal(total);
        page.setPageSize(form.getPageSize());
        page.setCurrentPage(form.getCurrentPage());
        if (!hostBOList.isEmpty()) {
            page.setData(hostBOList);
        }
        return page;
    }

    public HostBO infoHost(QueryBaseForm form) {
        HostBO hostBO = new HostBO();
        form.setFullField(true);
        List<HostBO> hostBOList = queryHost(form, false);
        if (!hostBOList.isEmpty()) {
            hostBO = hostBOList.get(0);
            List<String> ports = hostBO.getHosts().parallelStream().map(HostListBO::getPort).collect(Collectors.toList());
            Set<String> assembly = new HashSet<>();
            Set<String> services = new HashSet<>();
            for (HostListBO hostListBO : hostBO.getHosts()) {
                hostListBO.getApps().parallelStream()
                        .filter(app -> app.getName() != null)
                        .forEach(h -> assembly.add(h.getName()));

                hostListBO.getApps().parallelStream()
                        .filter(app -> app.getService() != null)
                        .forEach(h -> services.add(h.getService()));
            }
            hostBO.setPorts(ports);
            hostBO.setAssembly(assembly);
            hostBO.setServices(services);
        }
        return hostBO;
    }

    private List<HostBO> queryHost(QueryBaseForm form, boolean page) {
        String termName = "host_info";

        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        if (page) {
            sourceBuilder.query(getBoolQueryWithQueryForm(form));
            sourceBuilder.from(form.getCurrentPage() > 0 ? form.getCurrentPage() - 1 : form.getCurrentPage())
                    .size(form.getPageSize());
        } else {
            sourceBuilder.query(getBoolQueryFormInfo(form));
            sourceBuilder.size(1);
        }
        sourceBuilder.sort("@timestamp", SortOrder.DESC);

        sourceBuilder.fetchSource(INCLUDE_SOURCE_HOST, null).collapse(new CollapseBuilder("ip.keyword"));

        TermsAggregationBuilder ipsAgg = AggregationBuilders.terms(termName).field("ip.keyword").size(SIZE);
        ipsAgg.order(BucketOrder.aggregation("timestamp_order", false));

        MaxAggregationBuilder maxAggregationBuilder = AggregationBuilders.max("timestamp_order").field("@timestamp");
        ipsAgg.subAggregation(maxAggregationBuilder);

        sourceBuilder.aggregation(ipsAgg);
        log.info("ip_json: {}", sourceBuilder);
        request.source(sourceBuilder);

        SearchResponse response = search(request);
        if (response == null) {
            return Collections.emptyList();
        }
        SearchHits searchHits = response.getHits();

        Terms terms = response.getAggregations().get(termName);
        total = terms.getBuckets().size();

        List<HostBO> hostBOList = new ArrayList<>();
        for (SearchHit searchHit : searchHits) {
            String json = searchHit.getSourceAsString();
            HostBO hostBO = new Gson().fromJson(json, HostBO.class);
            form.setIp(hostBO.getIp());
            List<HostListBO> infoBOList = queryHostAndPort(form, page);
            hostBO.setHosts(infoBOList);
            hostBOList.add(hostBO);
        }
        return hostBOList;
    }

    private List<HostListBO> queryHostAndPort(QueryBaseForm form, boolean page) {
        String termName = "host_info";
        String stats = "times_count";
        String topHist = "top_score_hits";

        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        if (page) {
            sourceBuilder.query(getBoolQueryWithQueryForm(form));
        } else {
            sourceBuilder.query(getBoolQueryFormInfo(form));
        }
        sourceBuilder.sort("@timestamp", SortOrder.DESC);


        TermsAggregationBuilder portAgg = AggregationBuilders.terms(termName).field("port.keyword").size(SIZE);

        TopHitsAggregationBuilder topHitsAggregationBuilder = AggregationBuilders.topHits(topHist).size(1).sort("@timestamp", SortOrder.DESC);
        topHitsAggregationBuilder.fetchSource(form.isFullField() ? INCLUDE_SOURCE_HOST_INFO_ALL : INCLUDE_SOURCE_HOST_INFO, null);

        StatsAggregationBuilder statsAggregationBuilder = AggregationBuilders.stats(stats).field("@timestamp");

        MaxAggregationBuilder maxAggregationBuilder = AggregationBuilders.max("timestamp_order").field("@timestamp");

        portAgg.subAggregation(topHitsAggregationBuilder).subAggregation(statsAggregationBuilder).subAggregation(maxAggregationBuilder);


        sourceBuilder.aggregation(portAgg);
        log.info("ip_info_json: {}", sourceBuilder);
        request.source(sourceBuilder);

        SearchResponse response = search(request);
        if (response == null) {
            return Collections.emptyList();
        }
        Terms terms = response.getAggregations().get(termName);
        List<HostListBO> hostInfoBOList = new ArrayList<>();
        if (terms != null) {
            for (Terms.Bucket portTerms : terms.getBuckets()) {
                ParsedStats timeStats = portTerms.getAggregations().get(stats);
                ParsedTopHits hits = portTerms.getAggregations().get(topHist);
                String hitsJson = hits.getHits().getAt(0).getSourceAsString();
                HostListBO infoBO = new Gson().fromJson(hitsJson, HostListBO.class);
                infoBO.setCount(portTerms.getDocCount());
                infoBO.setMinDate(parseDate(timeStats.getMinAsString()));
                infoBO.setMaxDate(parseDate(timeStats.getMaxAsString()));
                hostInfoBOList.add(infoBO);
            }
        }
        return hostInfoBOList.parallelStream().sorted(Comparator.comparing(HostListBO::getMaxDate).reversed()).collect(Collectors.toList());
    }

    public Page<SiteBO> sitePage(QueryBaseForm form) {
        Page<SiteBO> page = new Page<>();
        List<SiteBO> siteBOList = querySite(form, true);
        page.setTotal(total);
        page.setPageSize(form.getPageSize());
        page.setCurrentPage(form.getCurrentPage());
        page.setData(siteBOList.parallelStream().sorted(Comparator.comparing(SiteBO::getTimestamp).reversed()).collect(Collectors.toList()));
        return page;
    }

    public SiteBO siteInfo(QueryBaseForm form) {
        form.setFullField(true);
        SiteBO siteBO = new SiteBO();
        List<SiteBO> siteBOList = querySite(form, false);
        if (siteBOList.size() > 0) {
            siteBO = siteBOList.get(0);
        }
        log.info("siteBO: {}", siteBO);
        return siteBO;
    }

    private List<SiteBO> querySite(QueryBaseForm form, boolean page) {
        String termName = "urls";

        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        if (page) {
            BoolQueryBuilder boolQueryBuilder = getBoolQueryWithQueryForm(form);
            boolQueryBuilder.filter(QueryBuilders.termQuery("pro.keyword", "HTTP"));
            sourceBuilder.query(boolQueryBuilder);
            sourceBuilder.from(form.getCurrentPage() > 0 ? form.getCurrentPage() - 1 : form.getCurrentPage())
                    .size(form.getPageSize());
        } else {
            sourceBuilder.query(getBoolQueryFormInfo(form));
            sourceBuilder.size(1);
        }
        sourceBuilder.sort("@timestamp", SortOrder.DESC);
        sourceBuilder.fetchSource(form.isFullField() ? INCLUDE_SOURCE_SITE_INFO_ALL : INCLUDE_SOURCE_SITE, null)
                .collapse(new CollapseBuilder("site.keyword"));

        TermsAggregationBuilder urlsAgg = AggregationBuilders.terms(termName).field("site.keyword").size(SIZE);
        urlsAgg.order(BucketOrder.aggregation("timestamp_order", false));

        MaxAggregationBuilder maxAgg = AggregationBuilders.max("timestamp_order").field("@timestamp");
        urlsAgg.subAggregation(maxAgg);

        sourceBuilder.aggregation(urlsAgg);

        log.info("urls_json: {}", sourceBuilder);
        request.source(sourceBuilder);

        SearchResponse response = search(request);
        if (response == null) {
            return Collections.emptyList();
        }

        Terms terms = response.getAggregations().get(termName);
        total = terms.getBuckets().size();

        SearchHits searchHits = response.getHits();
        List<SiteBO> siteList = new ArrayList<>();
        for (SearchHit searchHit : searchHits) {
            String json = searchHit.getSourceAsString();
            SiteBO siteBO = new Gson().fromJson(json, SiteBO.class);
            form.setSite(siteBO.getSite());
            SiteBO siteInfo = querySiteInfo(form, siteBO, page);
            siteList.add(siteInfo);
        }
        return siteList;
    }

    private SiteBO querySiteInfo(QueryBaseForm form, SiteBO site, boolean page) {
        String stats = "time_stats";
        String urlsChild = "urls_child";
        String topHits = "top_score_hits";
        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();
        if (page) {
            sourceBuilder.query(getBoolQueryWithQueryForm(form));
        } else {
            sourceBuilder.query(getBoolQueryFormInfo(form));
        }
        sourceBuilder.sort("@timestamp", SortOrder.DESC);

        TermsAggregationBuilder urlsChildAgg = AggregationBuilders.terms(urlsChild).field("url_tpl.keyword").size(SIZE);
        urlsChildAgg.order(BucketOrder.aggregation("timestamp_order", false));

        TopHitsAggregationBuilder topHitsAgg = AggregationBuilders.topHits(topHits)
                .fetchSource(INCLUDE_SOURCE_SITE_INFO, null).sort("@timestamp", SortOrder.DESC).size(1);

        StatsAggregationBuilder statsAgg = AggregationBuilders.stats(stats).field("@timestamp");

        MaxAggregationBuilder maxAgg = AggregationBuilders.max("timestamp_order").field("@timestamp");
        urlsChildAgg.subAggregation(topHitsAgg).subAggregation(maxAgg).subAggregation(statsAgg);

        sourceBuilder.aggregation(urlsChildAgg);

        log.info("child_json: {}", sourceBuilder);
        request.source(sourceBuilder);

        SearchResponse response = search(request);
        if (response == null) {
            return new SiteBO();
        }

        List<SiteInfoBO> siteList = new ArrayList<>();
        Set<String> apps = new HashSet<>();
        Set<String> siteType = new HashSet<>();
        Terms terms = response.getAggregations().get(urlsChild);
        long count = 0;
        for (Terms.Bucket bucket : terms.getBuckets()) {
            ParsedTopHits parsedTopHits = bucket.getAggregations().get(topHits);
            String json = parsedTopHits.getHits().getAt(0).getSourceAsString();
            SiteInfoBO siteInfo = new Gson().fromJson(json, SiteInfoBO.class);
            siteInfo.setPath(bucket.getKeyAsString());

            ParsedStats parsedStats = bucket.getAggregations().get(stats);
            siteInfo.setMinDate(parseDate(parsedStats.getMinAsString()));
            siteInfo.setMaxDate(parseDate(parsedStats.getMaxAsString()));
            siteList.add(siteInfo);

            count = bucket.getDocCount();
            if (siteInfo.getApps().size() > 0) {
                for (ApplicationVO app : siteInfo.getApps()) {
                    if (StringUtils.isNotBlank(app.getName())) {
                        apps.add(app.getName());
                    }
                    if (!app.getCategories().isEmpty()) {
                        app.getCategories().parallelStream().filter(c -> StringUtils.isNotBlank(c.getName()))
                                .forEach(c -> siteType.add(c.getName()));
                    }
                }
            }
        }
        site.setCount(count);
        site.setUrlNum(siteList.size());
        site.setApp(page ? Joiner.on(',').join(apps) : " ");
        site.setApps(page ? Collections.emptySet() : apps);
        site.setSiteType(page ? Collections.emptySet() : siteType);
        site.setSites(page ? Collections.emptyList() : siteList);
        return site;
    }

    public TopBO hostTop() {
        return top(0);
    }

    public TopBO siteTop() {
        return top(1);
    }

    private TopBO top(int type) {
        String pro = "pros";
        String app = "apps";
        String inner = "inners";
        String port = "ports";
        String country = "countrys";
        String os = "os";

        TopBO topBO = new TopBO();

        Map<String, List<TopInfoBO>> topInfoMap = new HashMap<>(16);

        ExecutorService executorService = Executors.newFixedThreadPool(6);
        Set<Callable<String>> callables = new HashSet<>();
        callables.add(() -> {
            topInfoMap.put(pro, topInfo(pro, "pro.keyword", type));
            return "pro";
        });
        callables.add(() -> {
            topInfoMap.put(app, topInfo(app, "apps.name.keyword", type));
            return "app";
        });
        callables.add(() -> {
            topInfoMap.put(inner, topInfo(inner, "inner", type));
            return "inner";
        });
        callables.add(() -> {
            topInfoMap.put(port, topInfo(port, "port.keyword", type));
            return "port";
        });
        callables.add(() -> {
            topInfoMap.put(country, topInfo(country, "geoip.country_name.keyword", type));
            return "country";
        });
        callables.add(() -> {
            topInfoMap.put(os, topInfo(os, "apps.os.keyword", type));
            return "os";
        });

        List<Future<String>> futures;
        try {
            futures = executorService.invokeAll(callables);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executorService.shutdown();

        topBO.setPros(topInfoMap.get(pro));
        topBO.setApps(topInfoMap.get(app));
        topBO.setInners(topInfoMap.get(inner));
        topBO.setPorts(topInfoMap.get(port));
        topBO.setCountry(topInfoMap.get(country));
        topBO.setOs(topInfoMap.get(os));

        return topBO;
    }

    private List<TopInfoBO> topInfo(String termName, String fieldName, int type) {
        String statsCount = "stats_count";

        SearchRequest request = getSearchRequest();
        SearchSourceBuilder sourceBuilder = getSourceBuilder();

        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms(termName).field(fieldName).size(SIZE);

        TermsAggregationBuilder statsCountAgg = AggregationBuilders.terms(statsCount).size(SIZE);

        if (type == 0) {
            if ("pros".equals(termName)) {
                statsCountAgg.field("host.keyword");
            } else {
                statsCountAgg.field("ip.keyword");
            }
        } else {
            statsCountAgg.field("site.keyword");
        }

        termsAggregationBuilder.subAggregation(statsCountAgg);

        sourceBuilder.aggregation(termsAggregationBuilder);
        log.info(termName + "_top_json: {}", sourceBuilder);
        request.source(sourceBuilder);

        SearchResponse response = search(request);
        if (response == null) {
            return Collections.emptyList();
        }
        Terms terms = response.getAggregations().get(termName);

        return setTopInfo(terms, statsCount);
    }

    private List<TopInfoBO> setTopInfo(Terms terms, String termName) {
        List<TopInfoBO> topList = new ArrayList<>();
        for (Terms.Bucket bucket : terms.getBuckets()) {
            TopInfoBO topInfo = new TopInfoBO();
            topInfo.setName(bucket.getKeyAsString());
            Terms nodeTerms = bucket.getAggregations().get(termName);
            topInfo.setCount(nodeTerms.getBuckets().size());
            topList.add(topInfo);
        }
        return limit(topList);
    }

    private List<TopInfoBO> limit(List<TopInfoBO> infoList) {
        if (infoList.size() <= 5) {
            return infoList;
        }
        return infoList.stream().sorted(Comparator.comparing(TopInfoBO::getCount).reversed()).limit(5L).collect(Collectors.toList());
    }

    private BoolQueryBuilder getBoolQueryFormInfo(QueryBaseForm form) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // 处理过的数据
        boolQueryBuilder.must(QueryBuilders.termQuery("state", 1));

        // IP
        if (StringUtils.isNotBlank(form.getIp())) {
            boolQueryBuilder.must(QueryBuilders.termQuery("ip", form.getIp()));
        }

        // site
        if (StringUtils.isNotBlank(form.getSite())) {
            boolQueryBuilder.must(QueryBuilders.termQuery("site.keyword", form.getSite()));
        }

        return boolQueryBuilder;
    }


    private BoolQueryBuilder getBoolQueryWithQueryForm(QueryBaseForm form) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // 处理过的数据
        boolQueryBuilder.must(QueryBuilders.termQuery("state", 1));

        // IP
        if (StringUtils.isNotBlank(form.getIp())) {
            boolQueryBuilder.must(QueryBuilders.termQuery("ip", form.getIp()));
        }

        // 端口
        if (StringUtils.isNotBlank(form.getPort())) {
            boolQueryBuilder.must(QueryBuilders.termQuery("port.keyword", form.getPort()));
        }

        // site
        if (StringUtils.isNotBlank(form.getSite())) {
            boolQueryBuilder.must(QueryBuilders.termQuery("site.keyword", form.getSite()));
        }

        // url
        if (StringUtils.isNotBlank(form.getUrl())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("url", form.getUrl().toLowerCase()).operator(Operator.AND));
        }

        // 指纹
        if (StringUtils.isNotBlank(form.getFinger())) {
            boolQueryBuilder.must(QueryBuilders.termQuery("apps.name", form.getFinger().toLowerCase()));
        }

        // type
        if (form.getPro() != null && !form.getPro().isEmpty()) {
            BoolQueryBuilder tmpBoolQueryBuilder = QueryBuilders.boolQuery();
            for (String pro : form.getPro()) {
                tmpBoolQueryBuilder.should(QueryBuilders.termQuery("pro.keyword", pro));
            }
            boolQueryBuilder.must(tmpBoolQueryBuilder);
        }

        // 分类ID
        if (form.getCategoryId() != null && !form.getCategoryId().isEmpty()) {
            BoolQueryBuilder tmpBoolQueryBuilder = QueryBuilders.boolQuery();
            for (Long id : form.getCategoryId()) {
                tmpBoolQueryBuilder.should(QueryBuilders.termQuery("apps.categories.id", id));
            }
            boolQueryBuilder.must(tmpBoolQueryBuilder);
        }

        // 筛选内网资产
        if (form.isInner()) {
            boolQueryBuilder.must(QueryBuilders.termQuery("inner", form.isInner()));
        }

        // 时间范围
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("@timestamp");
        if (form.getStart() != null) {
            rangeQueryBuilder.gte(form.getStart());
        }

        if (form.getEnd() != null) {
            rangeQueryBuilder.lte(form.getEnd());
        }

        // 时间都为空默认获取1天数据
        if (form.getStart() == null && form.getEnd() == null) {
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            c.add(Calendar.DATE, -1);
            rangeQueryBuilder.gte(c.getTime());
        }

        boolQueryBuilder.must(rangeQueryBuilder);

        return boolQueryBuilder;
    }

    private Date parseDate(String time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date();
        try {
            date = format.parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date;
    }

    private SearchResponse search(SearchRequest request) {
        try {
            log.debug("ES请求，地址: GET {}/_search, 内容: {}", request.indices()[0], request.source());
            return client.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

    private SearchRequest getSearchRequest() {
        return new SearchRequest(index + "-*");
    }

    private SearchSourceBuilder getSourceBuilder() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(0);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        return sourceBuilder;
    }

}
