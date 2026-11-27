-- PathPilot V10: Persist saved AI JD analyses on job_applications and AI summaries on monthly_reports.

ALTER TABLE job_applications
    ADD COLUMN ai_jd_analysis_json TEXT NULL AFTER prep_checklist_json,
    ADD COLUMN ai_jd_analyzed_at_utc DATETIME(6) NULL AFTER ai_jd_analysis_json;

ALTER TABLE monthly_reports
    ADD COLUMN ai_summary_json TEXT NULL AFTER summary_notes,
    ADD COLUMN ai_summary_generated_at_utc DATETIME(6) NULL AFTER ai_summary_json;
