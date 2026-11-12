package com.jobportal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_JOB_SEARCH     = "job_search";
    public static final String CACHE_JOB_COMPANY    = "job_company";
    public static final String CACHE_JOB_DETAIL     = "job_detail";
    public static final String CACHE_JOB_SIMILAR    = "job_similar";
    public static final String CACHE_FILTER_OPTIONS = "filter_options";
    public static final String CACHE_STATS          = "job_stats";
    public static final String CACHE_TICKER         = "job_ticker";
    public static final String CACHE_COMPANIES      = "companies";
    public static final String CACHE_COUNTRIES      = "job_countries";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheProperties cacheProperties;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper cacheMapper = objectMapper.copy();
        cacheMapper.activateDefaultTypingAsProperty(
                cacheMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                "@class"
        );
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(cacheMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        CACHE_JOB_SEARCH,     defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.getJobSearch())),
                        CACHE_JOB_COMPANY,    defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.getJobCompany())),
                        CACHE_JOB_DETAIL,     defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.getJobDetail())),
                        CACHE_JOB_SIMILAR,    defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.getJobSimilar())),
                        CACHE_FILTER_OPTIONS, defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.getFilterOptions())),
                        CACHE_STATS,          defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.getJobStats())),
                        CACHE_TICKER,         defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.getJobTicker())),
                        CACHE_COMPANIES,      defaultConfig.entryTtl(Duration.ofHours(6)),
                        CACHE_COUNTRIES,      defaultConfig.entryTtl(Duration.ofMinutes(360))
                ))
                .build();
    }
}
