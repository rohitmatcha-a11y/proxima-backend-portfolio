-- Proxima production scraper catalog
--
-- This intentionally imports only the approved company catalog and scraper
-- configuration. It does NOT import jobs, users, user job state, feedback,
-- scraper runs, Redis data, or Elasticsearch indexes.
--
-- It is safe to run again: companies are matched by slug and scraper configs
-- are matched by company. Runtime scraper counters and timestamps are reset so
-- the first production run starts cleanly.

WITH catalog (
    name,
    slug,
    website,
    logo_url,
    description,
    industry,
    headquarters_country,
    headquarters_city,
    company_size,
    size_category,
    linkedin_url,
    careers_page_url,
    is_active
) AS (
    VALUES
        (
            'Anthropic',
            'anthropic',
            'https://anthropic.com',
            'https://anthropic.com/favicon.ico',
            $company$Anthropic, PBC is an American artificial intelligence (AI) public benefit corporation headquartered in San Francisco, California, founded with the goal of promoting AI safety. Its flagship product is Claude, a series of large language models (LLMs). Anthropic was founded in 2021 by former members of OpenAI, including siblings Daniela Amodei and Dario Amodei, who are president and CEO, respectively. The company is privately held but plans to go public. It had an estimated valuation of $965 billion in May 2026, making it the most valuable pure-play AI company in the world.$company$,
            'TECHNOLOGY',
            'USA',
            'San Francisco',
            '1000-5000',
            'MID_SIZE',
            'https://www.linkedin.com/company/anthropic',
            'https://boards-api.greenhouse.io/v1/boards/anthropic/jobs',
            TRUE
        ),
        (
            'Coinbase',
            'coinbase',
            'https://coinbase.com',
            'https://www.coinbase.com/favicon.ico',
            $company$Coinbase Global, Inc. is an American cryptocurrency exchange. It was founded in 2012 by Brian Armstrong and Fred Ehrsam. Coinbase has over 100 million users, and is the largest U.S. based cryptocurrency exchange as well as the world's biggest bitcoin custodian, as of 2024. The company operates in more than 100 countries and holds nearly US$516 billion in assets, including nearly 12 percent of all bitcoin in existence and 11 percent of all staked Ether.$company$,
            'FINTECH',
            'USA',
            'San Francisco',
            '5000-10000',
            'LARGE',
            'https://www.linkedin.com/company/coinbase',
            'https://www.coinbase.com/careers/positions',
            TRUE
        ),
        (
            'Datadog',
            'datadog',
            'https://www.datadoghq.com',
            'https://careers.datadoghq.com/favicon.ico',
            $company$Datadog, Inc. is an American company that provides an observability service for cloud-scale applications, providing monitoring of servers, databases, tools, and services through a SaaS-based data analytics platform. Founded and headquartered in New York City, the company is a publicly traded entity on the Nasdaq stock exchange.$company$,
            'TECHNOLOGY',
            'US',
            'New York',
            NULL,
            NULL,
            'https://www.linkedin.com/company/datadog',
            'https://boards.greenhouse.io/datadog',
            TRUE
        ),
        (
            'Figma',
            'figma',
            'https://figma.com',
            'https://static.figma.com/app/icon/1/favicon.ico',
            $company$Figma, Inc. is an American company that develops software for user interface design. Founded in 2012 by Dylan Field and Evan Wallace, it provides a collaborative web-based application for interface design, along with companion desktop and mobile apps. Figma has 13 million users as of 2025.$company$,
            'TECHNOLOGY',
            'USA',
            'San Francisco',
            '1000-5000',
            'MID_SIZE',
            'https://www.linkedin.com/company/figma',
            'https://boards-api.greenhouse.io/v1/boards/figma/jobs',
            TRUE
        ),
        (
            'Google',
            'google',
            'https://google.com',
            'https://www.google.com/favicon.ico',
            $company$Google LLC is an American multinational technology corporation focused on information technology, online advertising, search engine technology, email, cloud computing, software, quantum computing, e-commerce, consumer electronics, and artificial intelligence (AI). It has been referred to as one of the world's most valuable brands. Google's parent company Alphabet Inc. is a Big Tech company.$company$,
            'TECHNOLOGY',
            'USA',
            'Mountain View',
            '10000+',
            'ENTERPRISE',
            'https://www.linkedin.com/company/google',
            'https://careers.google.com/jobs/results/',
            TRUE
        ),
        (
            'Linear',
            'linear',
            'https://linear.app/',
            'https://linear.app/favicon.ico?v=2',
            $company$Linear builds the purpose-built system for product development, helping teams plan, build, and ship products with speed and clarity.$company$,
            'OTHER',
            NULL,
            NULL,
            NULL,
            NULL,
            NULL,
            'https://jobs.ashbyhq.com/linear',
            TRUE
        ),
        (
            'Razorpay',
            'razorpay',
            'https://razorpay.com',
            'https://razorpay.com/favicon.ico',
            $company$Razorpay is India's leading full-stack financial services platform that enables businesses to accept payments, including domestic and international payments, automate banking operations, manage payroll, and access business credit. Trusted by over 150,000 businesses including 70% of India's unicorns, Razorpay offers payment gateway services, Razorpay POS for in-store payments, business banking through RazorpayX, payroll solutions, and corporate cards.$company$,
            'FINTECH',
            'India',
            'Bengaluru',
            '1000-5000',
            'MID_SIZE',
            'https://www.linkedin.com/company/razorpay',
            'https://razorpay.com/jobs/',
            TRUE
        ),
        (
            'Sentry',
            'sentry',
            'https://sentry.io/',
            'https://sentry.io/favicon.ico',
            $company$Application performance monitoring for developers and software teams to see errors clearer, solve issues faster, and continue learning continuously. Get started at sentry.io.$company$,
            'TECHNOLOGY',
            NULL,
            NULL,
            NULL,
            NULL,
            'https://www.linkedin.com/company/getsentry/',
            'https://jobs.ashbyhq.com/sentry',
            TRUE
        ),
        (
            'Stripe',
            'stripe',
            'https://stripe.com',
            'https://stripe.com/favicon.ico',
            $company$Stripe, Inc. is an Irish and American multinational financial services and software as a service company dual-headquartered in South San Francisco, California, United States, and Dublin, Ireland. The company primarily offers payment-processing software and application programming interfaces for e-commerce websites and mobile applications.$company$,
            'FINTECH',
            'USA',
            'San Francisco',
            '5000-10000',
            'LARGE',
            'https://www.linkedin.com/company/stripe',
            'https://stripe.com/jobs/search',
            TRUE
        )
)
INSERT INTO public.companies (
    created_at,
    updated_at,
    name,
    slug,
    website,
    logo_url,
    description,
    industry,
    headquarters_country,
    headquarters_city,
    size,
    size_category,
    linkedin_url,
    careers_page_url,
    is_active,
    total_jobs_scraped,
    last_scraped_at
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    name,
    slug,
    website,
    logo_url,
    description,
    industry,
    headquarters_country,
    headquarters_city,
    company_size,
    size_category,
    linkedin_url,
    careers_page_url,
    is_active,
    0,
    NULL
FROM catalog
ON CONFLICT (slug) DO UPDATE
SET
    updated_at = CURRENT_TIMESTAMP,
    name = EXCLUDED.name,
    website = EXCLUDED.website,
    logo_url = EXCLUDED.logo_url,
    description = EXCLUDED.description,
    industry = EXCLUDED.industry,
    headquarters_country = EXCLUDED.headquarters_country,
    headquarters_city = EXCLUDED.headquarters_city,
    size = EXCLUDED.size,
    size_category = EXCLUDED.size_category,
    linkedin_url = EXCLUDED.linkedin_url,
    careers_page_url = EXCLUDED.careers_page_url,
    is_active = EXCLUDED.is_active,
    total_jobs_scraped = 0,
    last_scraped_at = NULL;

WITH scraper_catalog (
    slug,
    scraper_class,
    cron_expression,
    is_enabled,
    rate_limit,
    custom_config
) AS (
    VALUES
        ('anthropic', NULL::text, '0 0 */6 * * *', TRUE, 0, NULL::text),
        ('coinbase', 'com.jobportal.service.scraper.impl.CoinbaseScraper', '0 0 */6 * * *', TRUE, 2000, NULL::text),
        ('datadog', 'com.jobportal.service.scraper.impl.UniversalCareerSiteScraper', '0 0 */6 * * *', TRUE, 750, '{"provider":"GREENHOUSE","board":"datadog","careersPageUrl":"https://boards.greenhouse.io/datadog"}'),
        ('figma', 'com.jobportal.service.scraper.impl.FigmaScraper', '0 0 */6 * * *', TRUE, 1000, NULL::text),
        ('google', 'com.jobportal.service.scraper.impl.GoogleScraper', '0 0 */6 * * *', TRUE, 3000, NULL::text),
        ('linear', 'com.jobportal.service.scraper.impl.UniversalCareerSiteScraper', '0 0 */6 * * *', TRUE, 750, '{"provider":"ASHBY","board":"linear","careersPageUrl":"https://jobs.ashbyhq.com/linear"}'),
        ('razorpay', 'com.jobportal.service.scraper.impl.RazorpayScraper', '0 0 */6 * * *', TRUE, 2000, NULL::text),
        ('sentry', 'com.jobportal.service.scraper.impl.UniversalCareerSiteScraper', '0 0 */6 * * *', TRUE, 750, '{"provider":"ASHBY","board":"sentry","careersPageUrl":"https://jobs.ashbyhq.com/sentry"}'),
        ('stripe', 'com.jobportal.service.scraper.impl.StripeScraper', '0 0 */6 * * *', TRUE, 2000, NULL::text)
)
INSERT INTO public.scraper_configs (
    created_at,
    updated_at,
    company_id,
    scraper_class,
    cron_expression,
    is_enabled,
    status,
    rate_limit,
    custom_config,
    last_run_at,
    last_success_at,
    last_error_at,
    last_error_message,
    total_runs,
    successful_runs,
    failed_runs,
    avg_jobs_per_run
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    company.id,
    scraper_catalog.scraper_class,
    scraper_catalog.cron_expression,
    scraper_catalog.is_enabled,
    CASE WHEN scraper_catalog.is_enabled THEN 'ACTIVE' ELSE 'INACTIVE' END,
    scraper_catalog.rate_limit,
    scraper_catalog.custom_config,
    NULL,
    NULL,
    NULL,
    NULL,
    0,
    0,
    0,
    0
FROM scraper_catalog
JOIN public.companies AS company ON company.slug = scraper_catalog.slug
ON CONFLICT (company_id) DO UPDATE
SET
    updated_at = CURRENT_TIMESTAMP,
    scraper_class = EXCLUDED.scraper_class,
    cron_expression = EXCLUDED.cron_expression,
    is_enabled = EXCLUDED.is_enabled,
    status = CASE WHEN EXCLUDED.is_enabled THEN 'ACTIVE' ELSE 'INACTIVE' END,
    rate_limit = EXCLUDED.rate_limit,
    custom_config = EXCLUDED.custom_config,
    last_run_at = NULL,
    last_success_at = NULL,
    last_error_at = NULL,
    last_error_message = NULL,
    total_runs = 0,
    successful_runs = 0,
    failed_runs = 0,
    avg_jobs_per_run = 0;

-- Expected result: exactly nine rows, all enabled and ready for their first run.
SELECT
    company.slug,
    company.name,
    company.careers_page_url,
    scraper_config.is_enabled,
    scraper_config.status
FROM public.companies AS company
JOIN public.scraper_configs AS scraper_config ON scraper_config.company_id = company.id
WHERE company.slug IN (
    'anthropic', 'coinbase', 'datadog', 'figma', 'google',
    'linear', 'razorpay', 'sentry', 'stripe'
)
ORDER BY company.name;
