-- MySQL dump 10.13  Distrib 8.4.10, for Linux (x86_64)
--
-- Host: localhost    Database: examine2
-- ------------------------------------------------------
-- Server version	8.4.10

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1.0.0','vs1 identity context','SQL','V1_0_0__vs1_identity_context.sql',563345225,'examine','2026-08-08 02:32:54',571,1),(2,'2.0.0','vs2 org permission','SQL','V2_0_0__vs2_org_permission.sql',1717203937,'examine','2026-08-08 02:32:56',1752,1),(3,'3.0.0','vs3 config publish','SQL','V3_0_0__vs3_config_publish.sql',-635008188,'examine','2026-08-08 02:32:56',898,1),(4,'3.1.0','vs3 reference integrity','SQL','V3_1_0__vs3_reference_integrity.sql',1166445989,'examine','2026-08-08 02:32:57',525,1),(5,'3.2.0','vs3 permission reference scope','SQL','V3_2_0__vs3_permission_reference_scope.sql',1316916204,'examine','2026-08-08 02:32:57',211,1),(6,'4.0.0','p4 a1 read runtime','SQL','V4_0_0__p4_a1_read_runtime.sql',1484090424,'examine','2026-08-08 02:32:58',210,1),(7,'4.1.0','p4 b2 record lifecycle','SQL','V4_1_0__p4_b2_record_lifecycle.sql',7006202,'examine','2026-08-08 02:32:58',121,1),(8,'4.2.0','p4 b3 query search permissions','SQL','V4_2_0__p4_b3_query_search_permissions.sql',594547462,'examine','2026-08-08 02:32:58',17,1),(9,'4.3.0','p4 b3 saved view','SQL','V4_3_0__p4_b3_saved_view.sql',752141600,'examine','2026-08-08 02:32:58',52,1),(10,'4.4.0','p4 c1 field value storage','SQL','V4_4_0__p4_c1_field_value_storage.sql',-435056449,'examine','2026-08-08 02:32:59',564,1),(11,'4.5.0','p4 c1 typed query unique','SQL','V4_5_0__p4_c1_typed_query_unique.sql',-128478633,'examine','2026-08-08 02:32:59',67,1),(12,'4.6.0','p4 c2 secure structured value storage','SQL','V4_6_0__p4_c2_secure_structured_value_storage.sql',1527925366,'examine','2026-08-08 02:32:59',191,1),(13,'4.7.0','p4 c2 typed query index authorization','SQL','V4_7_0__p4_c2_typed_query_index_authorization.sql',-1046739811,'examine','2026-08-08 02:33:00',618,1),(14,'4.8.0','p4 c2 rich text search','SQL','V4_8_0__p4_c2_rich_text_search.sql',1738108592,'examine','2026-08-08 02:33:00',117,1),(15,'4.9.0','p4 c3 relation reference subtable','SQL','V4_9_0__p4_c3_relation_reference_subtable.sql',666727873,'examine','2026-08-08 02:33:01',1176,1),(16,'4.10.0','p4 c4 derived field contract','SQL','V4_10_0__p4_c4_derived_field_contract.sql',-1607757314,'examine','2026-08-08 02:33:02',443,1),(17,'4.11.0','p4 c5 system fields','SQL','V4_11_0__p4_c5_system_fields.sql',256154511,'examine','2026-08-08 02:33:02',355,1),(18,'5.0.0','collab record team','SQL','V5_0_0__collab_record_team.sql',1522375100,'examine','2026-08-08 02:33:02',246,1),(19,'6.0.0','flow definition','SQL','V6_0_0__flow_definition.sql',-698436165,'examine','2026-08-08 02:33:03',183,1),(20,'7.0.0','work task','SQL','V7_0_0__work_task.sql',-922537507,'examine','2026-08-08 02:33:03',42,1),(21,'7.1.0','event message','SQL','V7_1_0__event_message.sql',-2142643507,'examine','2026-08-08 02:33:03',41,1),(22,'8.0.0','file object','SQL','V8_0_0__file_object.sql',-919116572,'examine','2026-08-08 02:33:03',78,1),(23,'8.1.0','feature api permissions','SQL','V8_1_0__feature_api_permissions.sql',-137241198,'examine','2026-08-08 02:33:03',11,1),(24,'8.2.0','collab record comment','SQL','V8_2_0__collab_record_comment.sql',-1198714265,'examine','2026-08-08 02:33:03',118,1),(25,'8.3.0','module record history','SQL','V8_3_0__module_record_history.sql',-1434441899,'examine','2026-08-08 02:33:03',95,1),(26,'8.4.0','flow sequential steps','SQL','V8_4_0__flow_sequential_steps.sql',-1568163343,'examine','2026-08-08 02:33:04',556,1),(27,'8.5.0','module favorite','SQL','V8_5_0__module_favorite.sql',427006960,'examine','2026-08-08 02:33:04',65,1),(28,'8.6.0','module recent record','SQL','V8_6_0__module_recent_record.sql',924938918,'examine','2026-08-08 02:33:04',107,1),(29,'8.7.0','flow instance withdrawal','SQL','V8_7_0__flow_instance_withdrawal.sql',997499310,'examine','2026-08-08 02:33:05',464,1),(30,'8.8.0','flow instance termination','SQL','V8_8_0__flow_instance_termination.sql',-813104470,'examine','2026-08-08 02:33:05',294,1),(31,'8.9.0','flow urge comment','SQL','V8_9_0__flow_urge_comment.sql',-1799594210,'examine','2026-08-08 02:33:05',64,1),(32,'8.10.0','flow transfer add sign','SQL','V8_10_0__flow_transfer_add_sign.sql',2032638094,'examine','2026-08-08 02:33:06',1174,1),(33,'8.11.0','flow return claim','SQL','V8_11_0__flow_return_claim.sql',637627607,'examine','2026-08-08 02:33:07',463,1),(34,'8.12.0','flow reduce sign copy','SQL','V8_12_0__flow_reduce_sign_copy.sql',2075490933,'examine','2026-08-08 02:33:07',213,1),(35,'8.13.0','module record flow state','SQL','V8_13_0__module_record_flow_state.sql',-1818716614,'examine','2026-08-08 02:33:07',45,1),(36,'8.14.0','flow record binding','SQL','V8_14_0__flow_record_binding.sql',1104746459,'examine','2026-08-08 02:33:08',102,1),(37,'8.15.0','flow definition record trigger','SQL','V8_15_0__flow_definition_record_trigger.sql',1826176480,'examine','2026-08-08 02:33:08',250,1),(38,'8.16.0','module record flow fanout','SQL','V8_16_0__module_record_flow_fanout.sql',1515375352,'examine','2026-08-08 02:33:08',67,1),(39,'8.17.0','flow trigger conditions fanout','SQL','V8_17_0__flow_trigger_conditions_fanout.sql',1373738921,'examine','2026-08-08 02:33:09',556,1),(40,'8.18.0','module record flow status mapping','SQL','V8_18_0__module_record_flow_status_mapping.sql',-1582099337,'examine','2026-08-08 02:33:09',189,1),(41,'8.19.0','flow definition record status mapping','SQL','V8_19_0__flow_definition_record_status_mapping.sql',-1005523305,'examine','2026-08-08 02:33:09',277,1),(42,'8.20.0','flow record event triggers','SQL','V8_20_0__flow_record_event_triggers.sql',-407292203,'examine','2026-08-08 02:33:10',269,1),(43,'8.21.0','openapi application foundation','SQL','V8_21_0__openapi_application_foundation.sql',-792521079,'examine','2026-08-08 02:33:10',338,1),(44,'8.22.0','module import job','SQL','V8_22_0__module_import_job.sql',-2092033320,'examine','2026-08-08 02:33:10',128,1),(45,'8.23.0','module xlsx export job','SQL','V8_23_0__module_xlsx_export_job.sql',411625858,'examine','2026-08-08 02:33:10',57,1),(46,'8.24.0','flow import completed trigger','SQL','V8_24_0__flow_import_completed_trigger.sql',1089745562,'examine','2026-08-08 02:33:11',268,1),(47,'8.25.0','module print template pdf','SQL','V8_25_0__module_print_template_pdf.sql',-95674264,'examine','2026-08-08 02:33:11',263,1),(48,'8.26.0','event message template delivery','SQL','V8_26_0__event_message_template_delivery.sql',1145422693,'examine','2026-08-08 02:33:11',178,1),(49,'8.27.0','collab record comment mention','SQL','V8_27_0__collab_record_comment_mention.sql',2125430321,'examine','2026-08-08 02:33:11',46,1),(50,'8.28.0','flow periodic trigger','SQL','V8_28_0__flow_periodic_trigger.sql',166903252,'examine','2026-08-08 02:33:12',226,1),(51,'8.29.0','flow conditional gateway','SQL','V8_29_0__flow_conditional_gateway.sql',-805169624,'examine','2026-08-08 02:33:12',166,1),(52,'8.30.0','flow approval modes','SQL','V8_30_0__flow_approval_modes.sql',246493378,'examine','2026-08-08 02:33:13',629,1),(53,'8.31.0','flow parallel branches','SQL','V8_31_0__flow_parallel_branches.sql',-521019405,'examine','2026-08-08 02:33:13',242,1),(54,'8.32.0','flow inclusive gateway','SQL','V8_32_0__flow_inclusive_gateway.sql',664610410,'examine','2026-08-08 02:33:13',264,1),(55,'8.33.0','flow approver sources','SQL','V8_33_0__flow_approver_sources.sql',-1348560246,'examine','2026-08-08 02:33:13',173,1),(56,'8.34.0','flow quorum approval','SQL','V8_34_0__flow_quorum_approval.sql',-1203931385,'examine','2026-08-08 02:33:14',814,1),(57,'8.35.0','flow approval deadlines','SQL','V8_35_0__flow_approval_deadlines.sql',-1795327038,'examine','2026-08-08 02:33:15',464,1),(58,'8.36.0','flow decision comment rules','SQL','V8_36_0__flow_decision_comment_rules.sql',-790781010,'examine','2026-08-08 02:33:15',404,1),(59,'8.37.0','flow delegation proxy','SQL','V8_37_0__flow_delegation_proxy.sql',-454293956,'examine','2026-08-08 02:33:15',170,1),(60,'8.38.0','plat reporting flow approvers','SQL','V8_38_0__plat_reporting_flow_approvers.sql',886687351,'examine','2026-08-08 02:33:16',182,1),(61,'8.39.0','flow ordered stages','SQL','V8_39_0__flow_ordered_stages.sql',-167300714,'examine','2026-08-08 02:33:16',467,1),(62,'8.40.0','flow branch ordered stages','SQL','V8_40_0__flow_branch_ordered_stages.sql',1046508889,'examine','2026-08-08 02:33:17',242,1),(63,'8.41.0','flow decision evidence templates','SQL','V8_41_0__flow_decision_evidence_templates.sql',-1952697323,'examine','2026-08-08 02:33:17',573,1),(64,'8.42.0','flow completion executions','SQL','V8_42_0__flow_completion_executions.sql',611842677,'examine','2026-08-08 02:33:18',733,1),(65,'8.43.0','flow subflow completion','SQL','V8_43_0__flow_subflow_completion.sql',-92771236,'examine','2026-08-08 02:33:19',626,1),(66,'8.44.0','flow parallel completion join','SQL','V8_44_0__flow_parallel_completion_join.sql',586346392,'examine','2026-08-08 02:33:19',221,1),(67,'8.45.0','flow completion compensation','SQL','V8_45_0__flow_completion_compensation.sql',-2026927551,'examine','2026-08-08 02:33:20',974,1),(68,'8.46.0','work project task views','SQL','V8_46_0__work_project_task_views.sql',462456114,'examine','2026-08-08 02:33:20',211,1),(69,'8.47.0','work daily report','SQL','V8_47_0__work_daily_report.sql',-1538421735,'examine','2026-08-08 02:33:20',81,1),(70,'8.48.0','work task reminder','SQL','V8_48_0__work_task_reminder.sql',-1539154049,'examine','2026-08-08 02:33:21',280,1),(71,'8.49.0','unified todo action center','SQL','V8_49_0__unified_todo_action_center.sql',-106665751,'examine','2026-08-08 02:33:21',170,1),(72,'8.50.0','module data source publish','SQL','V8_50_0__module_data_source_publish.sql',-1889911273,'examine','2026-08-08 02:33:21',332,1),(73,'8.51.0','module dashboard publish','SQL','V8_51_0__module_dashboard_publish.sql',285989031,'examine','2026-08-08 02:33:22',251,1),(74,'8.52.0','data source statistics dashboard','SQL','V8_52_0__data_source_statistics_dashboard.sql',1556733629,'examine','2026-08-08 02:33:22',157,1),(75,'8.53.0','module kpi target calculation','SQL','V8_53_0__module_kpi_target_calculation.sql',281727322,'examine','2026-08-08 02:33:22',485,1),(76,'8.54.0','module dashboard kpi widget','SQL','V8_54_0__module_dashboard_kpi_widget.sql',-666395124,'examine','2026-08-08 02:33:23',99,1),(77,'8.55.0','module report definition','SQL','V8_55_0__module_report_definition.sql',-937597015,'examine','2026-08-08 02:33:23',228,1),(78,'8.56.0','module report xlsx export','SQL','V8_56_0__module_report_xlsx_export.sql',1264415907,'examine','2026-08-08 02:33:23',79,1),(79,'8.57.0','module report schedule','SQL','V8_57_0__module_report_schedule.sql',-1937390890,'examine','2026-08-08 02:33:23',264,1),(80,'8.58.0','ai system read agent','SQL','V8_58_0__ai_system_read_agent.sql',-1640043999,'examine','2026-08-08 02:33:24',553,1),(81,'8.59.0','ai confirmed record write','SQL','V8_59_0__ai_confirmed_record_write.sql',-772560234,'examine','2026-08-08 02:33:24',225,1),(82,'8.60.0','ai fill runtime','SQL','V8_60_0__ai_fill_runtime.sql',-641684220,'examine','2026-08-08 02:33:25',1085,1),(83,'8.61.0','ai platform authorized system agent','SQL','V8_61_0__ai_platform_authorized_system_agent.sql',452449155,'examine','2026-08-08 02:33:26',685,1),(84,'8.62.0','ai platform task confirmation','SQL','V8_62_0__ai_platform_task_confirmation.sql',-1166513063,'examine','2026-08-08 02:33:26',366,1),(85,'8.63.0','ai platform operations query','SQL','V8_63_0__ai_platform_operations_query.sql',-1877534623,'examine','2026-08-08 02:33:27',159,1),(86,'8.64.0','ai configuration field draft','SQL','V8_64_0__ai_configuration_field_draft.sql',-631617499,'examine','2026-08-08 02:33:27',133,1),(87,'8.65.0','ai configuration artifact draft','SQL','V8_65_0__ai_configuration_artifact_draft.sql',-1246217319,'examine','2026-08-08 02:33:27',133,1),(88,'8.66.0','ai context read tool names','SQL','V8_66_0__ai_context_read_tool_names.sql',-1309268011,'examine','2026-08-08 02:33:27',64,1),(89,'8.67.0','ai work draft confirmation','SQL','V8_67_0__ai_work_draft_confirmation.sql',-397551172,'examine','2026-08-08 02:33:27',168,1),(90,'8.68.0','ai generated flow report print draft','SQL','V8_68_0__ai_generated_flow_report_print_draft.sql',308543718,'examine','2026-08-08 02:33:28',214,1),(91,'8.69.0','ai todo message read tool names','SQL','V8_69_0__ai_todo_message_read_tool_names.sql',1658730677,'examine','2026-08-08 02:33:28',63,1),(92,'8.70.0','ai work project metrics tool name','SQL','V8_70_0__ai_work_project_metrics_tool_name.sql',-1588140975,'examine','2026-08-08 02:33:28',67,1),(93,'8.71.0','ai record activity tool names','SQL','V8_71_0__ai_record_activity_tool_names.sql',-1463127701,'examine','2026-08-08 02:33:28',62,1),(94,'8.72.0','platform personal task lifecycle','SQL','V8_72_0__platform_personal_task_lifecycle.sql',-1046776590,'examine','2026-08-08 02:33:28',183,1),(95,'8.73.0','event delivery preference','SQL','V8_73_0__event_delivery_preference.sql',-129154566,'examine','2026-08-08 02:33:28',65,1),(96,'8.74.0','ai runtime statistics tool name','SQL','V8_74_0__ai_runtime_statistics_tool_name.sql',1818193671,'examine','2026-08-08 02:33:28',94,1),(97,'8.75.0','ai runtime report tool name','SQL','V8_75_0__ai_runtime_report_tool_name.sql',-1976076510,'examine','2026-08-08 02:33:28',60,1),(98,'8.76.0','ai configuration artifact suggestions','SQL','V8_76_0__ai_configuration_artifact_suggestions.sql',-686185467,'examine','2026-08-08 02:33:29',97,1),(99,'8.77.0','ai flow instance history tool name','SQL','V8_77_0__ai_flow_instance_history_tool_name.sql',792265441,'examine','2026-08-08 02:33:29',65,1),(100,'8.78.0','todo event message reminder cc','SQL','V8_78_0__todo_event_message_reminder_cc.sql',963641025,'examine','2026-08-08 02:33:29',184,1),(101,'8.79.0','plat password recovery','SQL','V8_79_0__plat_password_recovery.sql',1842699926,'examine','2026-08-08 02:33:29',33,1),(102,'8.80.0','file center list index','SQL','V8_80_0__file_center_list_index.sql',1958461404,'examine','2026-08-08 02:33:29',21,1),(103,'8.81.0','event multi channel delivery','SQL','V8_81_0__event_multi_channel_delivery.sql',-1574601289,'examine','2026-08-08 02:33:29',181,1),(104,'8.82.0','event channel configuration','SQL','V8_82_0__event_channel_configuration.sql',-1138430613,'examine','2026-08-08 02:33:29',34,1),(105,'8.83.0','enterprise identity mfa','SQL','V8_83_0__enterprise_identity_mfa.sql',694746626,'examine','2026-08-08 02:33:30',283,1),(106,'8.84.0','work configuration platform todo','SQL','V8_84_0__work_configuration_platform_todo.sql',-1524741868,'examine','2026-08-08 02:33:30',124,1),(107,'8.85.0','openapi callback subscription','SQL','V8_85_0__openapi_callback_subscription.sql',-2068039139,'examine','2026-08-08 02:33:30',134,1),(108,'8.86.0','platform system tenant lifecycle','SQL','V8_86_0__platform_system_tenant_lifecycle.sql',1386596925,'examine','2026-08-08 02:33:30',105,1),(109,'8.87.0','flow form node catalog','SQL','V8_87_0__flow_form_node_catalog.sql',-883161600,'examine','2026-08-08 02:33:30',164,1),(110,'8.88.0','dashboard scopes external consumption','SQL','V8_88_0__dashboard_scopes_external_consumption.sql',-186956616,'examine','2026-08-08 02:33:31',481,1),(111,'8.90.0','file field and print composition','SQL','V8_90_0__file_field_and_print_composition.sql',-1310579951,'examine','2026-08-08 02:33:31',124,1),(112,'8.91.0','system identity inheritance sync','SQL','V8_91_0__system_identity_inheritance_sync.sql',-1733463915,'examine','2026-08-08 02:33:31',252,1),(113,'8.92.0','platform openapi application','SQL','V8_92_0__platform_openapi_application.sql',-1132171,'examine','2026-08-08 02:33:31',160,1),(114,'8.93.0','system unified audit permission','SQL','V8_93_0__system_unified_audit_permission.sql',2003502554,'examine','2026-08-08 02:33:31',6,1),(115,'8.94.0','platform work todo message','SQL','V8_94_0__platform_work_todo_message.sql',1356965242,'examine','2026-08-08 02:33:32',199,1),(116,'8.95.0','platform flow dashboard','SQL','V8_95_0__platform_flow_dashboard.sql',1131455757,'examine','2026-08-08 02:33:32',509,1),(117,'8.96.0','platform lifecycle recovery settings','SQL','V8_96_0__platform_lifecycle_recovery_settings.sql',801868391,'examine','2026-08-08 02:33:33',485,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_confirmation`
--

DROP TABLE IF EXISTS `un_ai_agent_confirmation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_confirmation` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `operation` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `record_id` bigint DEFAULT NULL,
  `expected_record_version` bigint unsigned DEFAULT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `preview_json` json NOT NULL,
  `confidence_json` json NOT NULL,
  `clarifications_json` json NOT NULL,
  `sealed_ciphertext` mediumtext CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sealed_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sealed_command_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `owner_trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_confirmation_id` (`id`),
  UNIQUE KEY `uk_ai_confirmation_turn` (`system_id`,`tenant_id`,`turn_id`),
  KEY `idx_ai_confirmation_member` (`system_id`,`tenant_id`,`member_id`,`state`,`expires_at`,`id`),
  KEY `idx_ai_confirmation_record` (`system_id`,`tenant_id`,`module_code`,`record_id`,`created_at`),
  KEY `fk_ai_confirmation_session` (`system_id`,`tenant_id`,`session_id`),
  KEY `fk_ai_confirmation_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_confirmation_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_confirmation_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_confirmation_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_confirmation_session` FOREIGN KEY (`system_id`, `tenant_id`, `session_id`) REFERENCES `un_ai_agent_session` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_confirmation_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_confirmation_identity` CHECK (((`id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0) and (`schema_version_id` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_confirmation_json` CHECK (((json_type(`preview_json`) = _utf8mb4'OBJECT') and (json_type(`confidence_json`) = _utf8mb4'ARRAY') and (json_length(`confidence_json`) <= 128) and (json_type(`clarifications_json`) = _utf8mb4'ARRAY') and (json_length(`clarifications_json`) <= 20) and ((`result_json` is null) or (json_type(`result_json`) = _utf8mb4'OBJECT')))),
  CONSTRAINT `ck_ai_confirmation_module` CHECK (regexp_like(`module_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_ai_confirmation_operation` CHECK ((`operation` in (_utf8mb4'RECORD_CREATE',_utf8mb4'RECORD_UPDATE'))),
  CONSTRAINT `ck_ai_confirmation_record` CHECK ((((`operation` = _utf8mb4'RECORD_CREATE') and (`record_id` is null) and (`expected_record_version` is null)) or ((`operation` = _utf8mb4'RECORD_UPDATE') and (`record_id` > 0) and (`expected_record_version` is not null)))),
  CONSTRAINT `ck_ai_confirmation_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and (((`state` = _utf8mb4'PENDING') and (`confirmed_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'EXECUTING') and (`confirmed_by` > 0) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'SUCCEEDED') and (`confirmed_by` > 0) and (`result_json` is not null) and (`finished_at` is not null)) or ((`state` in (_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')) and (`result_json` is null) and (`finished_at` is not null))))),
  CONSTRAINT `ck_ai_confirmation_sealed` CHECK (((char_length(`sealed_ciphertext`) between 1 and 131072) and (char_length(`sealed_key_version`) between 1 and 64) and regexp_like(`sealed_command_hash`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`plan_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_ai_confirmation_state` CHECK ((`state` in (_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `ck_ai_confirmation_times` CHECK (((`expires_at` > `created_at`) and (`updated_at` >= `created_at`) and ((`finished_at` is null) or (`finished_at` >= `created_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_confirmation`
--

LOCK TABLES `un_ai_agent_confirmation` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_confirmation` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_confirmation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_confirmation_attempt`
--

DROP TABLE IF EXISTS `un_ai_agent_confirmation_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_confirmation_attempt` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `confirmation_id` bigint NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_confirmation_attempt_id` (`id`),
  UNIQUE KEY `uk_ai_confirmation_attempt_key` (`system_id`,`tenant_id`,`confirmation_id`,`request_key`),
  CONSTRAINT `fk_ai_confirmation_attempt_root` FOREIGN KEY (`system_id`, `tenant_id`, `confirmation_id`) REFERENCES `un_ai_agent_confirmation` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_confirmation_attempt_hash` CHECK ((regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`result_hash` is null) or regexp_like(`result_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_confirmation_attempt_identity` CHECK (((`id` > 0) and (`confirmation_id` > 0))),
  CONSTRAINT `ck_ai_confirmation_attempt_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
  CONSTRAINT `ck_ai_confirmation_attempt_result` CHECK (regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_ai_confirmation_attempt_state` CHECK ((((`status` = _utf8mb4'EXECUTING') and (`finished_at` is null) and (`result_hash` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')) and (`finished_at` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_confirmation_attempt`
--

LOCK TABLES `un_ai_agent_confirmation_attempt` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_confirmation_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_confirmation_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_confirmation_event`
--

DROP TABLE IF EXISTS `un_ai_agent_confirmation_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_confirmation_event` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `confirmation_id` bigint NOT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `from_state` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_state` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_confirmation_event_id` (`id`),
  KEY `idx_ai_confirmation_event_root` (`system_id`,`tenant_id`,`confirmation_id`,`revision`,`id`),
  CONSTRAINT `fk_ai_confirmation_event_root` FOREIGN KEY (`system_id`, `tenant_id`, `confirmation_id`) REFERENCES `un_ai_agent_confirmation` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_confirmation_event_identity` CHECK (((`id` > 0) and (`confirmation_id` > 0) and (`actor_member_id` > 0))),
  CONSTRAINT `ck_ai_confirmation_event_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_ai_confirmation_event_state` CHECK (((`to_state` in (_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')) and ((`from_state` is null) or (`from_state` in (_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))))),
  CONSTRAINT `ck_ai_confirmation_event_type` CHECK ((`event_type` in (_utf8mb4'PROPOSED',_utf8mb4'CONFIRMING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_confirmation_event`
--

LOCK TABLES `un_ai_agent_confirmation_event` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_confirmation_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_confirmation_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_message`
--

DROP TABLE IF EXISTS `un_ai_agent_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_message` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint DEFAULT NULL,
  `role` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `redacted_summary` varchar(500) NOT NULL,
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `character_count` int unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_message_id` (`id`),
  KEY `idx_ai_agent_message_session` (`system_id`,`tenant_id`,`session_id`,`created_at`,`id`),
  KEY `fk_ai_agent_message_turn` (`system_id`,`tenant_id`,`turn_id`),
  CONSTRAINT `fk_ai_agent_message_session` FOREIGN KEY (`system_id`, `tenant_id`, `session_id`) REFERENCES `un_ai_agent_session` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_message_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_message_hash` CHECK (regexp_like(`content_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_agent_message_identity` CHECK (((`id` > 0) and (`session_id` > 0) and ((`turn_id` is null) or (`turn_id` > 0)))),
  CONSTRAINT `ck_ai_agent_message_role` CHECK ((`role` in (_utf8mb4'USER',_utf8mb4'ASSISTANT'))),
  CONSTRAINT `ck_ai_agent_message_size` CHECK ((`character_count` <= 128000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_message`
--

LOCK TABLES `un_ai_agent_message` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_policy`
--

DROP TABLE IF EXISTS `un_ai_agent_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_policy` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `draft_revision` bigint unsigned NOT NULL,
  `draft_status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `draft_json` json NOT NULL,
  `max_rows` tinyint unsigned NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `redaction_mode` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `draft_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_policy_id` (`id`),
  UNIQUE KEY `uk_ai_agent_policy_tenant` (`system_id`,`tenant_id`),
  KEY `idx_ai_agent_policy_provider` (`system_id`,`tenant_id`,`provider_id`),
  KEY `fk_ai_agent_policy_active_version` (`system_id`,`tenant_id`,`active_version_id`),
  CONSTRAINT `fk_ai_agent_policy_active_version` FOREIGN KEY (`system_id`, `tenant_id`, `active_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_policy_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_policy_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_policy_active` CHECK (((`active_version_id` is null) or (`active_version_id` > 0))),
  CONSTRAINT `ck_ai_agent_policy_hash` CHECK (regexp_like(`draft_hash`,_ascii'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_agent_policy_identity` CHECK (((`id` > 0) and (`draft_revision` > 0) and (`provider_id` > 0) and (`updated_by` > 0) and (`provider_version` >= 0))),
  CONSTRAINT `ck_ai_agent_policy_json` CHECK ((json_type(`draft_json`) = _utf8mb4'OBJECT')),
  CONSTRAINT `ck_ai_agent_policy_limits` CHECK (((`max_rows` between 1 and 50) and (`enabled` in (0,1)))),
  CONSTRAINT `ck_ai_agent_policy_prompt` CHECK (regexp_like(`prompt_version`,_ascii'^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$')),
  CONSTRAINT `ck_ai_agent_policy_redaction` CHECK ((`redaction_mode` = _ascii'STRICT')),
  CONSTRAINT `ck_ai_agent_policy_status` CHECK ((`draft_status` in (_ascii'DRAFT',_ascii'CHECKED',_ascii'PUBLISHED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_policy`
--

LOCK TABLES `un_ai_agent_policy` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_policy` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_policy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_policy_check`
--

DROP TABLE IF EXISTS `un_ai_agent_policy_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_policy_check` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `policy_id` bigint NOT NULL,
  `draft_revision` bigint unsigned NOT NULL,
  `draft_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issues_json` json NOT NULL,
  `blocker_count` smallint unsigned NOT NULL,
  `checked_at` datetime(6) NOT NULL,
  `checked_by` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_policy_check_id` (`id`),
  UNIQUE KEY `uk_ai_agent_policy_check_revision` (`system_id`,`tenant_id`,`policy_id`,`draft_revision`),
  CONSTRAINT `fk_ai_agent_policy_check_root` FOREIGN KEY (`system_id`, `tenant_id`, `policy_id`) REFERENCES `un_ai_agent_policy` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_policy_check_hash` CHECK (regexp_like(`draft_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_agent_policy_check_identity` CHECK (((`id` > 0) and (`policy_id` > 0) and (`draft_revision` > 0) and (`checked_by` > 0))),
  CONSTRAINT `ck_ai_agent_policy_check_issues` CHECK (((json_type(`issues_json`) = _utf8mb4'ARRAY') and (json_length(`issues_json`) <= 256))),
  CONSTRAINT `ck_ai_agent_policy_check_status` CHECK ((((`status` = _utf8mb4'PASSED') and (`blocker_count` = 0)) or ((`status` = _utf8mb4'FAILED') and (`blocker_count` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_policy_check`
--

LOCK TABLES `un_ai_agent_policy_check` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_policy_check` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_policy_check` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_policy_publish`
--

DROP TABLE IF EXISTS `un_ai_agent_policy_publish`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_policy_publish` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `policy_id` bigint NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_policy_publish_id` (`id`),
  UNIQUE KEY `uk_ai_agent_policy_publish_request` (`system_id`,`tenant_id`,`policy_id`,`request_key`),
  KEY `fk_ai_agent_policy_publish_version` (`system_id`,`tenant_id`,`version_id`),
  CONSTRAINT `fk_ai_agent_policy_publish_root` FOREIGN KEY (`system_id`, `tenant_id`, `policy_id`) REFERENCES `un_ai_agent_policy` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_policy_publish_version` FOREIGN KEY (`system_id`, `tenant_id`, `version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_policy_publish_hash` CHECK (regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_agent_policy_publish_identity` CHECK (((`id` > 0) and (`policy_id` > 0) and (`version_id` > 0))),
  CONSTRAINT `ck_ai_agent_policy_publish_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_policy_publish`
--

LOCK TABLES `un_ai_agent_policy_publish` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_policy_publish` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_policy_publish` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_policy_version`
--

DROP TABLE IF EXISTS `un_ai_agent_policy_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_policy_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `policy_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `model_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `snapshot_json` json NOT NULL,
  `max_rows` tinyint unsigned NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `redaction_mode` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `published_at` datetime(6) NOT NULL,
  `published_by` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_policy_version_id` (`id`),
  UNIQUE KEY `uk_ai_agent_policy_version_no` (`system_id`,`tenant_id`,`policy_id`,`version_no`),
  KEY `idx_ai_agent_policy_version_provider` (`system_id`,`tenant_id`,`provider_id`,`provider_version`),
  CONSTRAINT `fk_ai_agent_policy_version_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_policy_version_root` FOREIGN KEY (`system_id`, `tenant_id`, `policy_id`) REFERENCES `un_ai_agent_policy` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_policy_version_hash` CHECK (regexp_like(`snapshot_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_agent_policy_version_identity` CHECK (((`id` > 0) and (`policy_id` > 0) and (`version_no` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`published_by` > 0))),
  CONSTRAINT `ck_ai_agent_policy_version_limits` CHECK (((`max_rows` between 1 and 50) and (`enabled` in (0,1)))),
  CONSTRAINT `ck_ai_agent_policy_version_redaction` CHECK ((`redaction_mode` = _utf8mb4'STRICT')),
  CONSTRAINT `ck_ai_agent_policy_version_snapshot` CHECK ((json_type(`snapshot_json`) = _utf8mb4'OBJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_policy_version`
--

LOCK TABLES `un_ai_agent_policy_version` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_policy_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_policy_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_session`
--

DROP TABLE IF EXISTS `un_ai_agent_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_session` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `model_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `title_summary` varchar(200) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_session_id` (`id`),
  KEY `idx_ai_agent_session_member` (`system_id`,`tenant_id`,`member_id`,`updated_at` DESC,`id` DESC),
  KEY `fk_ai_agent_session_member` (`system_id`,`member_id`,`tenant_id`),
  KEY `fk_ai_agent_session_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_agent_session_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_agent_session_member` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_session_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_session_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_session_identity` CHECK (((`id` > 0) and (`member_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0))),
  CONSTRAINT `ck_ai_agent_session_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CLOSED'))),
  CONSTRAINT `ck_ai_agent_session_times` CHECK ((`updated_at` >= `created_at`)),
  CONSTRAINT `ck_ai_agent_session_title` CHECK ((char_length(trim(`title_summary`)) between 1 and 200))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_session`
--

LOCK TABLES `un_ai_agent_session` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_tool_call`
--

DROP TABLE IF EXISTS `un_ai_agent_tool_call`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_tool_call` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `tool_name` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `response_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_count` smallint unsigned NOT NULL,
  `latency_ms` bigint unsigned NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_tool_call_id` (`id`),
  KEY `idx_ai_agent_tool_call_turn` (`system_id`,`tenant_id`,`turn_id`,`id`),
  CONSTRAINT `fk_ai_agent_tool_call_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_tool_call_hash` CHECK ((regexp_like(`request_hash`,_ascii'^[0-9a-f]{64}$') and ((`response_hash` is null) or regexp_like(`response_hash`,_ascii'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_agent_tool_call_identity` CHECK (((`id` > 0) and (`turn_id` > 0))),
  CONSTRAINT `ck_ai_agent_tool_call_name` CHECK ((`tool_name` in (_utf8mb4'RECORD_QUERY',_utf8mb4'RECORD_CONTEXT_SUMMARY',_utf8mb4'WORK_TASK_QUERY',_utf8mb4'WORK_DAILY_REPORT_QUERY',_utf8mb4'TODO_QUERY',_utf8mb4'MESSAGE_QUERY',_utf8mb4'WORK_PROJECT_METRICS_QUERY',_utf8mb4'RECORD_COMMENT_QUERY',_utf8mb4'RECORD_HISTORY_QUERY',_utf8mb4'RECORD_FILE_QUERY',_utf8mb4'RUNTIME_STATISTICS_QUERY',_utf8mb4'RUNTIME_REPORT_QUERY',_utf8mb4'FLOW_INSTANCE_HISTORY_QUERY'))),
  CONSTRAINT `ck_ai_agent_tool_call_result` CHECK (((`result_count` <= 50) and regexp_like(`result_code`,_ascii'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_ai_agent_tool_call_status` CHECK ((`status` in (_ascii'SUCCEEDED',_ascii'FAILED',_ascii'RETRYABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_tool_call`
--

LOCK TABLES `un_ai_agent_tool_call` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_tool_call` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_tool_call` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_turn`
--

DROP TABLE IF EXISTS `un_ai_agent_turn`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_turn` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_summary` varchar(500) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `response_summary` varchar(500) DEFAULT NULL,
  `response_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `returned_rows` smallint unsigned NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `retryable` tinyint(1) NOT NULL,
  `latency_ms` bigint unsigned NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_turn_id` (`id`),
  KEY `idx_ai_agent_turn_session` (`system_id`,`tenant_id`,`session_id`,`created_at`,`id`),
  KEY `idx_ai_agent_turn_trace` (`trace_id`,`created_at`),
  KEY `fk_ai_agent_turn_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_agent_turn_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_agent_turn_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_turn_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_agent_turn_session` FOREIGN KEY (`system_id`, `tenant_id`, `session_id`) REFERENCES `un_ai_agent_session` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_turn_hashes` CHECK ((regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`plan_hash` is null) or regexp_like(`plan_hash`,_utf8mb4'^[0-9a-f]{64}$')) and ((`response_hash` is null) or regexp_like(`response_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_agent_turn_identity` CHECK (((`id` > 0) and (`session_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0))),
  CONSTRAINT `ck_ai_agent_turn_result` CHECK (((`returned_rows` <= 50) and (`retryable` in (0,1)) and regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_ai_agent_turn_state` CHECK ((((`status` = _utf8mb4'RUNNING') and (`finished_at` is null)) or ((`status` <> _utf8mb4'RUNNING') and (`finished_at` is not null)))),
  CONSTRAINT `ck_ai_agent_turn_status` CHECK ((`status` in (_utf8mb4'RUNNING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'RETRYABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_turn`
--

LOCK TABLES `un_ai_agent_turn` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_turn` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_turn` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_agent_usage`
--

DROP TABLE IF EXISTS `un_ai_agent_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_agent_usage` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `provider_calls` tinyint unsigned NOT NULL,
  `prompt_tokens` int unsigned NOT NULL,
  `completion_tokens` int unsigned NOT NULL,
  `total_tokens` int unsigned NOT NULL,
  `provider_latency_ms` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_agent_usage_id` (`id`),
  UNIQUE KEY `uk_ai_agent_usage_turn` (`system_id`,`tenant_id`,`turn_id`),
  CONSTRAINT `fk_ai_agent_usage_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_agent_usage_counts` CHECK (((`provider_calls` between 0 and 8) and (`total_tokens` = (`prompt_tokens` + `completion_tokens`)))),
  CONSTRAINT `ck_ai_agent_usage_identity` CHECK (((`id` > 0) and (`turn_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_agent_usage`
--

LOCK TABLES `un_ai_agent_usage` WRITE;
/*!40000 ALTER TABLE `un_ai_agent_usage` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_agent_usage` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_config_artifact_attempt`
--

DROP TABLE IF EXISTS `un_ai_config_artifact_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_config_artifact_attempt` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_config_artifact_attempt_id` (`id`),
  UNIQUE KEY `uk_ai_config_artifact_attempt_key` (`system_id`,`tenant_id`,`proposal_id`,`action`,`request_key`),
  CONSTRAINT `fk_ai_config_artifact_attempt_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_config_artifact_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_config_artifact_attempt_action` CHECK ((`action` = _utf8mb4'CONFIRM')),
  CONSTRAINT `ck_ai_config_artifact_attempt_hash` CHECK ((regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`result_hash` is null) or regexp_like(`result_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_config_artifact_attempt_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0))),
  CONSTRAINT `ck_ai_config_artifact_attempt_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
  CONSTRAINT `ck_ai_config_artifact_attempt_result` CHECK (regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_ai_config_artifact_attempt_state` CHECK ((((`status` = _utf8mb4'EXECUTING') and (`finished_at` is null) and (`result_hash` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')) and (`finished_at` is not null) and (`result_hash` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_config_artifact_attempt`
--

LOCK TABLES `un_ai_config_artifact_attempt` WRITE;
/*!40000 ALTER TABLE `un_ai_config_artifact_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_config_artifact_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_config_artifact_event`
--

DROP TABLE IF EXISTS `un_ai_config_artifact_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_config_artifact_event` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `attempt_id` bigint DEFAULT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `from_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_config_artifact_event_id` (`id`),
  KEY `idx_ai_config_artifact_event_root` (`system_id`,`tenant_id`,`proposal_id`,`revision`,`id`),
  KEY `fk_ai_config_artifact_event_attempt` (`system_id`,`tenant_id`,`attempt_id`),
  CONSTRAINT `fk_ai_config_artifact_event_attempt` FOREIGN KEY (`system_id`, `tenant_id`, `attempt_id`) REFERENCES `un_ai_config_artifact_attempt` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_artifact_event_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_config_artifact_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_config_artifact_event_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0) and ((`attempt_id` is null) or (`attempt_id` > 0)) and (`actor_member_id` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_config_artifact_event_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_ai_config_artifact_event_state` CHECK (((`to_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')) and ((`from_state` is null) or (`from_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))))),
  CONSTRAINT `ck_ai_config_artifact_event_type` CHECK ((`event_type` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PROPOSED',_utf8mb4'CONFIRMING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_config_artifact_event`
--

LOCK TABLES `un_ai_config_artifact_event` WRITE;
/*!40000 ALTER TABLE `un_ai_config_artifact_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_config_artifact_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_config_artifact_proposal`
--

DROP TABLE IF EXISTS `un_ai_config_artifact_proposal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_config_artifact_proposal` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `artifact_kind` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `preview_json` json DEFAULT NULL,
  `confidence` decimal(6,5) NOT NULL,
  `clarification_summary` varchar(500) DEFAULT NULL,
  `sealed_ciphertext` mediumtext CHARACTER SET ascii COLLATE ascii_bin,
  `sealed_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `sealed_command_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `acted_by` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_config_artifact_proposal_id` (`id`),
  UNIQUE KEY `uk_ai_config_artifact_proposal_turn` (`system_id`,`tenant_id`,`turn_id`),
  KEY `idx_ai_config_artifact_member` (`system_id`,`tenant_id`,`member_id`,`state`,`expires_at`,`id`),
  KEY `idx_ai_config_artifact_module` (`system_id`,`tenant_id`,`module_code`,`artifact_kind`,`state`,`created_at`,`id`),
  KEY `fk_ai_config_artifact_session` (`system_id`,`tenant_id`,`session_id`),
  KEY `fk_ai_config_artifact_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_config_artifact_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_config_artifact_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_artifact_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_artifact_session` FOREIGN KEY (`system_id`, `tenant_id`, `session_id`) REFERENCES `un_ai_agent_session` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_artifact_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_config_artifact_confidence` CHECK ((`confidence` between 0.00000 and 1.00000)),
  CONSTRAINT `ck_ai_config_artifact_hash` CHECK (regexp_like(`plan_hash`,_ascii'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_config_artifact_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_config_artifact_json` CHECK ((((`preview_json` is null) or (json_type(`preview_json`) = _utf8mb4'OBJECT')) and ((`result_json` is null) or (json_type(`result_json`) = _utf8mb4'OBJECT')))),
  CONSTRAINT `ck_ai_config_artifact_kind` CHECK ((`artifact_kind` in (_utf8mb4'SELECTION_FIELD',_utf8mb4'PAGE_LAYOUT',_utf8mb4'FILTER_SCENARIO',_utf8mb4'FIELD_PERMISSION_STAGE'))),
  CONSTRAINT `ck_ai_config_artifact_module` CHECK (((`module_code` is null) or regexp_like(`module_code`,_ascii'^[a-z][a-z0-9_]{1,63}$'))),
  CONSTRAINT `ck_ai_config_artifact_payload` CHECK ((((`state` = _ascii'CLARIFICATION_REQUIRED') and (`module_code` is null) and (`preview_json` is null) and (`clarification_summary` is not null) and (`sealed_ciphertext` is null) and (`sealed_key_version` is null) and (`sealed_command_hash` is null)) or ((`state` <> _ascii'CLARIFICATION_REQUIRED') and (`module_code` is not null) and (`preview_json` is not null) and (`clarification_summary` is null) and (char_length(`sealed_ciphertext`) between 1 and 131072) and (char_length(`sealed_key_version`) between 1 and 64) and regexp_like(`sealed_command_hash`,_ascii'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_config_artifact_result` CHECK ((regexp_like(`result_code`,_ascii'^[A-Z][A-Z0-9_]{1,63}$') and (((`state` in (_ascii'CLARIFICATION_REQUIRED',_ascii'PENDING')) and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _ascii'EXECUTING') and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _ascii'SUCCEEDED') and (`acted_by` > 0) and (`result_json` is not null) and (`finished_at` is not null)) or ((`state` in (_ascii'FAILED',_ascii'REJECTED')) and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is not null)) or ((`state` = _ascii'EXPIRED') and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is not null))))),
  CONSTRAINT `ck_ai_config_artifact_state` CHECK ((`state` in (_ascii'CLARIFICATION_REQUIRED',_ascii'PENDING',_ascii'EXECUTING',_ascii'SUCCEEDED',_ascii'FAILED',_ascii'REJECTED',_ascii'EXPIRED'))),
  CONSTRAINT `ck_ai_config_artifact_time` CHECK (((`expires_at` > `created_at`) and (`updated_at` >= `created_at`) and ((`finished_at` is null) or (`finished_at` >= `created_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_config_artifact_proposal`
--

LOCK TABLES `un_ai_config_artifact_proposal` WRITE;
/*!40000 ALTER TABLE `un_ai_config_artifact_proposal` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_config_artifact_proposal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_config_field_attempt`
--

DROP TABLE IF EXISTS `un_ai_config_field_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_config_field_attempt` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_config_field_attempt_id` (`id`),
  UNIQUE KEY `uk_ai_config_field_attempt_key` (`system_id`,`tenant_id`,`proposal_id`,`action`,`request_key`),
  CONSTRAINT `fk_ai_config_field_attempt_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_config_field_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_config_field_attempt_action` CHECK ((`action` = _utf8mb4'CONFIRM')),
  CONSTRAINT `ck_ai_config_field_attempt_hash` CHECK ((regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`result_hash` is null) or regexp_like(`result_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_config_field_attempt_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0))),
  CONSTRAINT `ck_ai_config_field_attempt_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
  CONSTRAINT `ck_ai_config_field_attempt_result` CHECK (regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_ai_config_field_attempt_state` CHECK ((((`status` = _utf8mb4'EXECUTING') and (`finished_at` is null) and (`result_hash` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')) and (`finished_at` is not null) and (`result_hash` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_config_field_attempt`
--

LOCK TABLES `un_ai_config_field_attempt` WRITE;
/*!40000 ALTER TABLE `un_ai_config_field_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_config_field_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_config_field_event`
--

DROP TABLE IF EXISTS `un_ai_config_field_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_config_field_event` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `attempt_id` bigint DEFAULT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `from_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_config_field_event_id` (`id`),
  KEY `idx_ai_config_field_event_root` (`system_id`,`tenant_id`,`proposal_id`,`revision`,`id`),
  KEY `fk_ai_config_field_event_attempt` (`system_id`,`tenant_id`,`attempt_id`),
  CONSTRAINT `fk_ai_config_field_event_attempt` FOREIGN KEY (`system_id`, `tenant_id`, `attempt_id`) REFERENCES `un_ai_config_field_attempt` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_field_event_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_config_field_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_config_field_event_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0) and ((`attempt_id` is null) or (`attempt_id` > 0)) and (`actor_member_id` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_config_field_event_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_ai_config_field_event_state` CHECK (((`to_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')) and ((`from_state` is null) or (`from_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))))),
  CONSTRAINT `ck_ai_config_field_event_type` CHECK ((`event_type` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PROPOSED',_utf8mb4'CONFIRMING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_config_field_event`
--

LOCK TABLES `un_ai_config_field_event` WRITE;
/*!40000 ALTER TABLE `un_ai_config_field_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_config_field_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_config_field_proposal`
--

DROP TABLE IF EXISTS `un_ai_config_field_proposal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_config_field_proposal` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `preview_json` json DEFAULT NULL,
  `confidence` decimal(6,5) NOT NULL,
  `clarification_summary` varchar(500) DEFAULT NULL,
  `sealed_ciphertext` mediumtext CHARACTER SET ascii COLLATE ascii_bin,
  `sealed_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `sealed_command_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `acted_by` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `owner_trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_config_field_proposal_id` (`id`),
  UNIQUE KEY `uk_ai_config_field_proposal_turn` (`system_id`,`tenant_id`,`turn_id`),
  KEY `idx_ai_config_field_member` (`system_id`,`tenant_id`,`member_id`,`state`,`expires_at`,`id`),
  KEY `idx_ai_config_field_module` (`system_id`,`tenant_id`,`module_code`,`state`,`created_at`,`id`),
  KEY `fk_ai_config_field_session` (`system_id`,`tenant_id`,`session_id`),
  KEY `fk_ai_config_field_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_config_field_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_config_field_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_field_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_field_session` FOREIGN KEY (`system_id`, `tenant_id`, `session_id`) REFERENCES `un_ai_agent_session` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_config_field_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_config_field_confidence` CHECK ((`confidence` between 0.00000 and 1.00000)),
  CONSTRAINT `ck_ai_config_field_hash` CHECK (regexp_like(`plan_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_config_field_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_config_field_json` CHECK ((((`preview_json` is null) or (json_type(`preview_json`) = _utf8mb4'OBJECT')) and ((`result_json` is null) or (json_type(`result_json`) = _utf8mb4'OBJECT')))),
  CONSTRAINT `ck_ai_config_field_module` CHECK (((`module_code` is null) or regexp_like(`module_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$'))),
  CONSTRAINT `ck_ai_config_field_payload` CHECK ((((`state` = _utf8mb4'CLARIFICATION_REQUIRED') and (`module_code` is null) and (`preview_json` is null) and (`clarification_summary` is not null) and (`sealed_ciphertext` is null) and (`sealed_key_version` is null) and (`sealed_command_hash` is null)) or ((`state` <> _utf8mb4'CLARIFICATION_REQUIRED') and (`module_code` is not null) and (`preview_json` is not null) and (`clarification_summary` is null) and (char_length(`sealed_ciphertext`) between 1 and 131072) and (char_length(`sealed_key_version`) between 1 and 64) and regexp_like(`sealed_command_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_config_field_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and (((`state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING')) and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'EXECUTING') and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'SUCCEEDED') and (`acted_by` > 0) and (`result_json` is not null) and (`finished_at` is not null)) or ((`state` in (_utf8mb4'FAILED',_utf8mb4'REJECTED')) and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is not null)) or ((`state` = _utf8mb4'EXPIRED') and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is not null))))),
  CONSTRAINT `ck_ai_config_field_state` CHECK ((`state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `ck_ai_config_field_time` CHECK (((`expires_at` > `created_at`) and (`updated_at` >= `created_at`) and ((`finished_at` is null) or (`finished_at` >= `created_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_config_field_proposal`
--

LOCK TABLES `un_ai_config_field_proposal` WRITE;
/*!40000 ALTER TABLE `un_ai_config_field_proposal` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_config_field_proposal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_fill_attempt`
--

DROP TABLE IF EXISTS `un_ai_fill_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_fill_attempt` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `proposal_id` bigint NOT NULL,
  `action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_fill_attempt_id` (`id`),
  UNIQUE KEY `uk_ai_fill_attempt_key` (`system_id`,`tenant_id`,`member_id`,`action`,`request_key`),
  KEY `idx_ai_fill_attempt_proposal` (`system_id`,`tenant_id`,`proposal_id`,`action`,`created_at`),
  CONSTRAINT `ck_ai_fill_attempt_action` CHECK ((`action` in (_utf8mb4'PROPOSE',_utf8mb4'CONFIRM',_utf8mb4'REJECT'))),
  CONSTRAINT `ck_ai_fill_attempt_identity` CHECK (((`id` > 0) and (`member_id` > 0) and (`proposal_id` > 0))),
  CONSTRAINT `ck_ai_fill_attempt_key` CHECK ((regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_ai_fill_attempt_status` CHECK ((((`status` = _utf8mb4'EXECUTING') and (`finished_at` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED')) and (`finished_at` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_fill_attempt`
--

LOCK TABLES `un_ai_fill_attempt` WRITE;
/*!40000 ALTER TABLE `un_ai_fill_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_fill_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_fill_event`
--

DROP TABLE IF EXISTS `un_ai_fill_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_fill_event` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `proposal_id` bigint NOT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `from_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_fill_event_id` (`id`),
  KEY `idx_ai_fill_event_proposal` (`system_id`,`tenant_id`,`proposal_id`,`revision`,`id`),
  CONSTRAINT `fk_ai_fill_event_proposal` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_fill_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_fill_event_identity` CHECK (((`id` > 0) and (`proposal_id` > 0) and (`actor_member_id` > 0))),
  CONSTRAINT `ck_ai_fill_event_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_ai_fill_event_state` CHECK (((`to_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')) and ((`from_state` is null) or (`from_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))))),
  CONSTRAINT `ck_ai_fill_event_type` CHECK ((`event_type` in (_utf8mb4'PROPOSED',_utf8mb4'CONFIRMING',_utf8mb4'REJECTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_fill_event`
--

LOCK TABLES `un_ai_fill_event` WRITE;
/*!40000 ALTER TABLE `un_ai_fill_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_fill_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_fill_proposal`
--

DROP TABLE IF EXISTS `un_ai_fill_proposal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_fill_proposal` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `record_id` bigint NOT NULL,
  `field_id` bigint NOT NULL,
  `field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `field_name` varchar(160) NOT NULL,
  `result_schema` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expected_record_version` bigint unsigned NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `source_version_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `model_code` varchar(160) NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `sources_json` json NOT NULL,
  `before_display_value` text,
  `after_display_value` text,
  `confidence` decimal(6,5) NOT NULL,
  `clarification_summary` varchar(500) DEFAULT NULL,
  `is_overwrite` tinyint(1) NOT NULL,
  `result_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `sealed_ciphertext` mediumtext CHARACTER SET ascii COLLATE ascii_bin,
  `sealed_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `sealed_command_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `acted_by` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `owner_trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `prompt_tokens` int unsigned NOT NULL,
  `completion_tokens` int unsigned NOT NULL,
  `provider_latency_ms` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_fill_proposal_id` (`id`),
  KEY `idx_ai_fill_proposal_member` (`system_id`,`tenant_id`,`member_id`,`module_code`,`record_id`,`field_code`,`created_at` DESC,`id` DESC),
  KEY `idx_ai_fill_proposal_target` (`system_id`,`tenant_id`,`module_code`,`record_id`,`field_code`,`state`,`id`),
  KEY `fk_ai_fill_proposal_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_fill_proposal_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_fill_proposal_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_fill_proposal_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_fill_proposal_confidence` CHECK ((`confidence` between 0.00000 and 1.00000)),
  CONSTRAINT `ck_ai_fill_proposal_hash` CHECK ((regexp_like(`source_version_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`result_hash` is null) or regexp_like(`result_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_fill_proposal_identity` CHECK (((`id` > 0) and (`member_id` > 0) and (`record_id` > 0) and (`field_id` > 0) and (`schema_version_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_fill_proposal_json` CHECK (((json_type(`sources_json`) = _utf8mb4'ARRAY') and (json_length(`sources_json`) between 1 and 16) and ((`result_json` is null) or (json_type(`result_json`) = _utf8mb4'OBJECT')))),
  CONSTRAINT `ck_ai_fill_proposal_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and (((`state` in (_utf8mb4'PENDING',_utf8mb4'CLARIFICATION_REQUIRED')) and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'EXECUTING') and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'SUCCEEDED') and (`acted_by` > 0) and (`result_json` is not null) and (`finished_at` is not null)) or ((`state` in (_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')) and (`result_json` is null) and (`finished_at` is not null))))),
  CONSTRAINT `ck_ai_fill_proposal_sealed` CHECK ((((`state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'FAILED',_utf8mb4'EXPIRED')) and (`sealed_ciphertext` is null) and (`sealed_key_version` is null) and (`sealed_command_hash` is null)) or ((char_length(`sealed_ciphertext`) between 1 and 131072) and (char_length(`sealed_key_version`) between 1 and 64) and regexp_like(`sealed_command_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_fill_proposal_state` CHECK ((`state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `ck_ai_fill_proposal_target` CHECK ((regexp_like(`module_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`result_schema` in (_utf8mb4'STRING',_utf8mb4'DECIMAL',_utf8mb4'INTEGER',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'BOOLEAN')))),
  CONSTRAINT `ck_ai_fill_proposal_time` CHECK (((`expires_at` > `created_at`) and (`updated_at` >= `created_at`) and ((`finished_at` is null) or (`finished_at` >= `created_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_fill_proposal`
--

LOCK TABLES `un_ai_fill_proposal` WRITE;
/*!40000 ALTER TABLE `un_ai_fill_proposal` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_fill_proposal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_generated_draft_attempt`
--

DROP TABLE IF EXISTS `un_ai_generated_draft_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_generated_draft_attempt` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_generated_draft_attempt_id` (`id`),
  UNIQUE KEY `uk_ai_generated_draft_attempt_key` (`system_id`,`tenant_id`,`proposal_id`,`action`,`request_key`),
  KEY `idx_ai_generated_draft_attempt_proposal` (`system_id`,`tenant_id`,`proposal_id`,`id`),
  CONSTRAINT `fk_ai_generated_draft_attempt_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_generated_draft_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_generated_draft_attempt_action` CHECK ((`action` = _utf8mb4'CONFIRM')),
  CONSTRAINT `ck_ai_generated_draft_attempt_hash` CHECK ((regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`result_hash` is null) or regexp_like(`result_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_generated_draft_attempt_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0))),
  CONSTRAINT `ck_ai_generated_draft_attempt_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
  CONSTRAINT `ck_ai_generated_draft_attempt_result` CHECK (regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_ai_generated_draft_attempt_state` CHECK ((((`status` = _utf8mb4'EXECUTING') and (`finished_at` is null) and (`result_hash` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')) and (`finished_at` is not null) and (`result_hash` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_generated_draft_attempt`
--

LOCK TABLES `un_ai_generated_draft_attempt` WRITE;
/*!40000 ALTER TABLE `un_ai_generated_draft_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_generated_draft_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_generated_draft_event`
--

DROP TABLE IF EXISTS `un_ai_generated_draft_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_generated_draft_event` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `attempt_id` bigint DEFAULT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `from_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_generated_draft_event_id` (`id`),
  KEY `idx_ai_generated_draft_event_proposal` (`system_id`,`tenant_id`,`proposal_id`,`id`),
  KEY `fk_ai_generated_draft_event_attempt` (`system_id`,`tenant_id`,`attempt_id`),
  CONSTRAINT `fk_ai_generated_draft_event_attempt` FOREIGN KEY (`system_id`, `tenant_id`, `attempt_id`) REFERENCES `un_ai_generated_draft_attempt` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_generated_draft_event_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_generated_draft_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_generated_draft_event_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0) and ((`attempt_id` is null) or (`attempt_id` > 0)) and (`actor_member_id` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_generated_draft_event_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_ai_generated_draft_event_state` CHECK (((`to_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')) and ((`from_state` is null) or (`from_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED'))))),
  CONSTRAINT `ck_ai_generated_draft_event_type` CHECK ((`event_type` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PROPOSED',_utf8mb4'CONFIRMING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_generated_draft_event`
--

LOCK TABLES `un_ai_generated_draft_event` WRITE;
/*!40000 ALTER TABLE `un_ai_generated_draft_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_generated_draft_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_generated_draft_proposal`
--

DROP TABLE IF EXISTS `un_ai_generated_draft_proposal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_generated_draft_proposal` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `preview_json` json DEFAULT NULL,
  `confidence` decimal(6,5) NOT NULL,
  `clarification_summary` varchar(500) DEFAULT NULL,
  `command_ciphertext` mediumtext CHARACTER SET ascii COLLATE ascii_bin,
  `command_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `command_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `acted_by` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_generated_draft_proposal_id` (`id`),
  UNIQUE KEY `uk_ai_generated_draft_proposal_turn` (`system_id`,`tenant_id`,`turn_id`),
  KEY `idx_ai_generated_draft_proposal_owner` (`system_id`,`tenant_id`,`member_id`,`session_id`,`id`),
  KEY `idx_ai_generated_draft_proposal_state` (`system_id`,`tenant_id`,`state`,`expires_at`,`id`),
  KEY `fk_ai_generated_draft_proposal_session` (`system_id`,`tenant_id`,`session_id`),
  KEY `fk_ai_generated_draft_proposal_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_generated_draft_proposal_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_generated_draft_proposal_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_generated_draft_proposal_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_generated_draft_proposal_session` FOREIGN KEY (`system_id`, `tenant_id`, `session_id`) REFERENCES `un_ai_agent_session` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_generated_draft_proposal_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_generated_draft_proposal_confidence` CHECK ((`confidence` between 0.00000 and 1.00000)),
  CONSTRAINT `ck_ai_generated_draft_proposal_hash` CHECK (regexp_like(`plan_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_generated_draft_proposal_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_generated_draft_proposal_json` CHECK ((((`preview_json` is null) or (json_type(`preview_json`) = _utf8mb4'OBJECT')) and ((`result_json` is null) or (json_type(`result_json`) = _utf8mb4'OBJECT')))),
  CONSTRAINT `ck_ai_generated_draft_proposal_operation` CHECK ((`operation` in (_utf8mb4'FLOW_DEFINITION_DRAFT',_utf8mb4'CONFIG_REPORT_DRAFT',_utf8mb4'CONFIG_PRINT_TEMPLATE_DRAFT'))),
  CONSTRAINT `ck_ai_generated_draft_proposal_payload` CHECK ((((`state` = _utf8mb4'CLARIFICATION_REQUIRED') and (`preview_json` is null) and (`clarification_summary` is not null) and (`command_ciphertext` is null) and (`command_key_version` is null) and (`command_hash` is null)) or ((`state` <> _utf8mb4'CLARIFICATION_REQUIRED') and (`preview_json` is not null) and (`clarification_summary` is null) and (char_length(`command_ciphertext`) between 1 and 262144) and (char_length(`command_key_version`) between 1 and 64) and regexp_like(`command_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_generated_draft_proposal_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and (((`state` = _utf8mb4'CLARIFICATION_REQUIRED') and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'PENDING') and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'EXECUTING') and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'SUCCEEDED') and (`acted_by` > 0) and (`result_json` is not null) and (`finished_at` is not null)) or ((`state` in (_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')) and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is not null)) or ((`state` = _utf8mb4'EXPIRED') and ((`acted_by` is null) or (`acted_by` > 0)) and (`result_json` is null) and (`finished_at` is not null))))),
  CONSTRAINT `ck_ai_generated_draft_proposal_state` CHECK ((`state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED'))),
  CONSTRAINT `ck_ai_generated_draft_proposal_time` CHECK (((`expires_at` > `created_at`) and (`updated_at` >= `created_at`) and ((`finished_at` is null) or (`finished_at` >= `created_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_generated_draft_proposal`
--

LOCK TABLES `un_ai_generated_draft_proposal` WRITE;
/*!40000 ALTER TABLE `un_ai_generated_draft_proposal` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_generated_draft_proposal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_provider`
--

DROP TABLE IF EXISTS `un_ai_provider`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_provider` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `provider_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_name` varchar(160) NOT NULL,
  `base_url` varchar(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `timeout_seconds` tinyint unsigned NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_provider_id` (`id`),
  UNIQUE KEY `uk_ai_provider_code` (`system_id`,`tenant_id`,`provider_code`),
  KEY `idx_ai_provider_list` (`system_id`,`tenant_id`,`updated_at` DESC,`id` DESC),
  CONSTRAINT `fk_ai_provider_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_provider_code` CHECK (regexp_like(`provider_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_ai_provider_identity` CHECK (((`id` > 0) and (`created_by` > 0) and (`updated_by` > 0) and (`version` >= 0))),
  CONSTRAINT `ck_ai_provider_limits` CHECK (((`timeout_seconds` between 1 and 30) and (`enabled` in (0,1)))),
  CONSTRAINT `ck_ai_provider_model` CHECK ((char_length(trim(`model_code`)) between 1 and 128)),
  CONSTRAINT `ck_ai_provider_name` CHECK ((char_length(trim(`provider_name`)) between 1 and 160)),
  CONSTRAINT `ck_ai_provider_secret_ref` CHECK (((char_length(`secret_ref`) between 3 and 512) and (`secret_ref` = trim(`secret_ref`)) and regexp_like(`secret_ref`,_utf8mb4'^[A-Za-z][A-Za-z0-9+.-]{1,31}://[^[:space:]]+$'))),
  CONSTRAINT `ck_ai_provider_times` CHECK ((`updated_at` >= `created_at`)),
  CONSTRAINT `ck_ai_provider_url` CHECK (((char_length(`base_url`) between 8 and 1024) and (not((`base_url` like _utf8mb4'%@%'))) and (not((`base_url` like _utf8mb4'%?%'))) and (not((`base_url` like _utf8mb4'%#%')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_provider`
--

LOCK TABLES `un_ai_provider` WRITE;
/*!40000 ALTER TABLE `un_ai_provider` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_provider` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_work_attempt`
--

DROP TABLE IF EXISTS `un_ai_work_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_work_attempt` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_work_attempt_id` (`id`),
  UNIQUE KEY `uk_ai_work_attempt_key` (`system_id`,`tenant_id`,`proposal_id`,`action`,`request_key`),
  CONSTRAINT `fk_ai_work_attempt_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_work_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_work_attempt_action` CHECK ((`action` = _utf8mb4'CONFIRM')),
  CONSTRAINT `ck_ai_work_attempt_hash` CHECK ((regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`result_hash` is null) or regexp_like(`result_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_work_attempt_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0))),
  CONSTRAINT `ck_ai_work_attempt_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
  CONSTRAINT `ck_ai_work_attempt_result` CHECK (regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_ai_work_attempt_state` CHECK ((((`status` = _utf8mb4'EXECUTING') and (`finished_at` is null) and (`result_hash` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')) and (`finished_at` is not null) and (`result_hash` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_work_attempt`
--

LOCK TABLES `un_ai_work_attempt` WRITE;
/*!40000 ALTER TABLE `un_ai_work_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_work_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_work_event`
--

DROP TABLE IF EXISTS `un_ai_work_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_work_event` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `attempt_id` bigint DEFAULT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `from_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_work_event_id` (`id`),
  KEY `idx_ai_work_event_root` (`system_id`,`tenant_id`,`proposal_id`,`revision`,`id`),
  KEY `fk_ai_work_event_attempt` (`system_id`,`tenant_id`,`attempt_id`),
  CONSTRAINT `fk_ai_work_event_attempt` FOREIGN KEY (`system_id`, `tenant_id`, `attempt_id`) REFERENCES `un_ai_work_attempt` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_work_event_root` FOREIGN KEY (`system_id`, `tenant_id`, `proposal_id`) REFERENCES `un_ai_work_proposal` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_work_event_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0) and ((`attempt_id` is null) or (`attempt_id` > 0)) and (`actor_member_id` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_work_event_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_ai_work_event_state` CHECK (((`to_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')) and ((`from_state` is null) or (`from_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED'))))),
  CONSTRAINT `ck_ai_work_event_type` CHECK ((`event_type` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PROPOSED',_utf8mb4'CONFIRMING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_work_event`
--

LOCK TABLES `un_ai_work_event` WRITE;
/*!40000 ALTER TABLE `un_ai_work_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_work_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_ai_work_proposal`
--

DROP TABLE IF EXISTS `un_ai_work_proposal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_ai_work_proposal` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `preview_json` json DEFAULT NULL,
  `confidence` decimal(6,5) NOT NULL,
  `clarification_summary` varchar(500) DEFAULT NULL,
  `command_ciphertext` mediumtext CHARACTER SET ascii COLLATE ascii_bin,
  `command_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `command_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `acted_by` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_ai_work_proposal_id` (`id`),
  UNIQUE KEY `uk_ai_work_proposal_turn` (`system_id`,`tenant_id`,`turn_id`),
  KEY `idx_ai_work_proposal_member` (`system_id`,`tenant_id`,`member_id`,`state`,`expires_at`,`id`),
  KEY `idx_ai_work_proposal_operation` (`system_id`,`tenant_id`,`operation`,`state`,`created_at`,`id`),
  KEY `fk_ai_work_proposal_session` (`system_id`,`tenant_id`,`session_id`),
  KEY `fk_ai_work_proposal_policy` (`system_id`,`tenant_id`,`policy_version_id`),
  KEY `fk_ai_work_proposal_provider` (`system_id`,`tenant_id`,`provider_id`),
  CONSTRAINT `fk_ai_work_proposal_policy` FOREIGN KEY (`system_id`, `tenant_id`, `policy_version_id`) REFERENCES `un_ai_agent_policy_version` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_work_proposal_provider` FOREIGN KEY (`system_id`, `tenant_id`, `provider_id`) REFERENCES `un_ai_provider` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_work_proposal_session` FOREIGN KEY (`system_id`, `tenant_id`, `session_id`) REFERENCES `un_ai_agent_session` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ai_work_proposal_turn` FOREIGN KEY (`system_id`, `tenant_id`, `turn_id`) REFERENCES `un_ai_agent_turn` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_work_proposal_confidence` CHECK ((`confidence` between 0.00000 and 1.00000)),
  CONSTRAINT `ck_ai_work_proposal_hash` CHECK (regexp_like(`plan_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_ai_work_proposal_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`member_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_ai_work_proposal_json` CHECK ((((`preview_json` is null) or (json_type(`preview_json`) = _utf8mb4'OBJECT')) and ((`result_json` is null) or (json_type(`result_json`) = _utf8mb4'OBJECT')))),
  CONSTRAINT `ck_ai_work_proposal_operation` CHECK ((`operation` in (_utf8mb4'WORK_TASK_DRAFT',_utf8mb4'WORK_DAILY_REPORT_DRAFT'))),
  CONSTRAINT `ck_ai_work_proposal_payload` CHECK ((((`state` = _utf8mb4'CLARIFICATION_REQUIRED') and (`preview_json` is null) and (`clarification_summary` is not null) and (`command_ciphertext` is null) and (`command_key_version` is null) and (`command_hash` is null)) or ((`state` <> _utf8mb4'CLARIFICATION_REQUIRED') and (`preview_json` is not null) and (`clarification_summary` is null) and (char_length(`command_ciphertext`) between 1 and 262144) and (char_length(`command_key_version`) between 1 and 64) and regexp_like(`command_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_ai_work_proposal_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and (((`state` = _utf8mb4'CLARIFICATION_REQUIRED') and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'PENDING') and (`acted_by` is null) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'EXECUTING') and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is null)) or ((`state` = _utf8mb4'SUCCEEDED') and (`acted_by` > 0) and (`result_json` is not null) and (`finished_at` is not null)) or ((`state` in (_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED')) and (`acted_by` > 0) and (`result_json` is null) and (`finished_at` is not null)) or ((`state` = _utf8mb4'EXPIRED') and ((`acted_by` is null) or (`acted_by` > 0)) and (`result_json` is null) and (`finished_at` is not null))))),
  CONSTRAINT `ck_ai_work_proposal_state` CHECK ((`state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED',_utf8mb4'STALE',_utf8mb4'PERMISSION_DENIED'))),
  CONSTRAINT `ck_ai_work_proposal_time` CHECK (((`expires_at` > `created_at`) and (`updated_at` >= `created_at`) and ((`finished_at` is null) or (`finished_at` >= `created_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_ai_work_proposal`
--

LOCK TABLES `un_ai_work_proposal` WRITE;
/*!40000 ALTER TABLE `un_ai_work_proposal` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_ai_work_proposal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_audit_operation`
--

DROP TABLE IF EXISTS `un_audit_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_audit_operation` (
  `id` bigint NOT NULL,
  `operation_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_account_id` bigint DEFAULT NULL,
  `context_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `source_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `before_json` json DEFAULT NULL,
  `after_json` json DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_operation_aggregate` (`aggregate_type`,`aggregate_id`,`created_at`),
  KEY `idx_audit_operation_context` (`system_id`,`tenant_id`,`created_at`),
  KEY `idx_audit_operation_trace` (`trace_id`),
  CONSTRAINT `ck_audit_operation_context` CHECK ((`context_type` in (_utf8mb4'PLATFORM',_utf8mb4'SYSTEM'))),
  CONSTRAINT `ck_audit_operation_result` CHECK ((`result` in (_utf8mb4'SUCCESS',_utf8mb4'DENIED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_audit_operation`
--

LOCK TABLES `un_audit_operation` WRITE;
/*!40000 ALTER TABLE `un_audit_operation` DISABLE KEYS */;
INSERT INTO `un_audit_operation` VALUES (2085919578929790977,'HTTP_REQUEST_DENIED','HTTP_REQUEST','96fba4f4-2e23-4cca-befc-223a675c19bf',NULL,'PLATFORM',NULL,NULL,'WEB','96fba4f4-2e23-4cca-befc-223a675c19bf','fbcf8743426e433ebe2a6ab2b19d6509','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 02:42:36.805'),(2085920205751746562,'PLATFORM_SYSTEM_CREATED','PLATFORM_SYSTEM','2085920203721703426',2085917350597300225,'PLATFORM',NULL,NULL,'WEB','923f885d-ce79-463d-9131-ba987c3611de','e5bb20293a8042a2a5ee02407766028c','SUCCESS',NULL,'{\"id\": \"2085920203721703426\", \"code\": \"cycle118_acceptance\", \"name\": \"Cycle118 \裓閈蔦障低砛", \"status\": \"ACTIVE\", \"version\": \"1\", \"createdAt\": \"2026-08-08T02:45:05.748370576\", \"tenantMode\": \"SINGLE\", \"description\": \"\覾肻覾赲譢頫终功\能、\衆診能、可穃肻蝄蔦衆診覾牖指碶裓萘穃", \"ownerAccountId\": \"2085917350597300225\"}',NULL,'2026-08-08 02:45:06.236'),(2085920300517851138,'HTTP_REQUEST_DENIED','HTTP_REQUEST','fa40c22c-ad10-4abf-b1d3-57c559f03756',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','fa40c22c-ad10-4abf-b1d3-57c559f03756','34f049d790574271bd5a295539cf7f00','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:45:28.826'),(2085920712192983042,'HTTP_REQUEST_FAILED','HTTP_REQUEST','b670db8b-93ba-4c22-a067-12c2f0870ce1',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','b670db8b-93ba-4c22-a067-12c2f0870ce1','e6d9244d1b8d43e3af340352968a3835','FAILED','{\"path\": \"/api/v1/systems/2085920203721703426/admin/config/preview\", \"method\": \"GET\"}',NULL,'INTERNAL_ERROR','2026-08-08 02:47:06.977'),(2085922329669746689,'HTTP_REQUEST_DENIED','HTTP_REQUEST','2240dfd7-d948-4f98-9df6-72d29fd81fda',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','2240dfd7-d948-4f98-9df6-72d29fd81fda','31f9e9eee4a94145a9aa872e8e7a0303','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:53:32.615'),(2085922343800356865,'HTTP_REQUEST_DENIED','HTTP_REQUEST','4d8e0d29-f0e2-4150-b77c-00c1dd54d946',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','4d8e0d29-f0e2-4150-b77c-00c1dd54d946','33c65ac528d54d74a4bfb4be0e2892c8','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:53:35.983'),(2085923656718172162,'HTTP_REQUEST_DENIED','HTTP_REQUEST','8e9888a4-ed3e-4ef9-8fb2-bad424bc2384',NULL,'PLATFORM',NULL,NULL,'WEB','8e9888a4-ed3e-4ef9-8fb2-bad424bc2384','eedbd07ce5224bee8a9cdedb4d84d59e','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 02:58:49.007'),(2085923791309193217,'HTTP_REQUEST_DENIED','HTTP_REQUEST','7536d2a0-bfab-4e73-be1f-65fc41d476a7',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','7536d2a0-bfab-4e73-be1f-65fc41d476a7','eacf4da4bb26406385a995fecb039d7d','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:59:21.096'),(2085923795516080130,'HTTP_REQUEST_DENIED','HTTP_REQUEST','b6aa7a0f-81ca-4846-845b-6efd312d5497',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','b6aa7a0f-81ca-4846-845b-6efd312d5497','f84069cce7a949cbaee4a02ea46391c9','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:59:22.099'),(2085923798112354306,'HTTP_REQUEST_DENIED','HTTP_REQUEST','a6c6797d-9998-4f7c-becd-10dc44e1d2e2',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','a6c6797d-9998-4f7c-becd-10dc44e1d2e2','c49152fe88d4497f86989f983fcc0d6a','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:59:22.718'),(2085923898578518017,'HTTP_REQUEST_DENIED','HTTP_REQUEST','f5bcef39-cd87-494a-af12-787945883887',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','f5bcef39-cd87-494a-af12-787945883887','9297a29e34844aaaa6ba91034a8655de','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:59:46.671'),(2085923902147870722,'HTTP_REQUEST_DENIED','HTTP_REQUEST','1746e5b6-2be9-4b54-9a67-02677cf4f24d',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','1746e5b6-2be9-4b54-9a67-02677cf4f24d','255dca216fc749d8a0d7c9e04e3d4587','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:59:47.522'),(2085923904555401217,'HTTP_REQUEST_DENIED','HTTP_REQUEST','98bab631-9f5d-4c09-9b0b-b539e78e8499',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','98bab631-9f5d-4c09-9b0b-b539e78e8499','18a336ee9bb64cb1b14b522cc9e2df5e','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 02:59:48.095'),(2085926585756176386,'HTTP_REQUEST_DENIED','HTTP_REQUEST','9d7d1f35-b8e3-4469-bbf0-4912dabb894d',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','9d7d1f35-b8e3-4469-bbf0-4912dabb894d','91a71f4f1b7a447880d74cb3a3357fdd','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:10:27.374'),(2085926693545594882,'HTTP_REQUEST_DENIED','HTTP_REQUEST','1fccb4ca-1561-4d8e-83fe-ce709db6e0eb',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','1fccb4ca-1561-4d8e-83fe-ce709db6e0eb','366940fbc07944a8ac248cf444ab9480','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:10:53.043'),(2085926697567932417,'HTTP_REQUEST_DENIED','HTTP_REQUEST','3cebce6d-cc81-415f-9dcb-db7a9a6af00d',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','3cebce6d-cc81-415f-9dcb-db7a9a6af00d','d8061583c6944b0c8a0a1eac3088a5a3','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:10:54.002'),(2085926699979657217,'HTTP_REQUEST_DENIED','HTTP_REQUEST','d061df18-05c6-439c-89f1-b28bc58c24d1',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','d061df18-05c6-439c-89f1-b28bc58c24d1','d09c30c64273423f8590e7726b753324','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:10:54.577'),(2085926956494901249,'HTTP_REQUEST_DENIED','HTTP_REQUEST','c2d7ebf9-8d8e-4e86-b92d-e3d43ac43c1b',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','c2d7ebf9-8d8e-4e86-b92d-e3d43ac43c1b','5de229bf3a4c4a839baa63c232719be1','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:11:55.735'),(2085926960785674241,'HTTP_REQUEST_DENIED','HTTP_REQUEST','16dcaeff-69ef-4b7e-8656-cbfc7d209614',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','16dcaeff-69ef-4b7e-8656-cbfc7d209614','1be2861862cf463ea1676e3982eda2f8','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:11:56.759'),(2085926963163844610,'HTTP_REQUEST_DENIED','HTTP_REQUEST','2eabb789-c7f2-4e56-aa88-10589aa68966',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','2eabb789-c7f2-4e56-aa88-10589aa68966','a2a5bdac766d44a28429aa76551c5158','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:11:57.326'),(2085927050115960834,'HTTP_REQUEST_DENIED','HTTP_REQUEST','0a2fe3d1-a4fd-496d-a580-e1e83a44290f',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','0a2fe3d1-a4fd-496d-a580-e1e83a44290f','3ac19a10963141a68929081f8475686f','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:12:18.056'),(2085927052494131202,'HTTP_REQUEST_DENIED','HTTP_REQUEST','f32f2384-c1fb-4c72-937c-14a42c51c0fd',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','f32f2384-c1fb-4c72-937c-14a42c51c0fd','594c7e0edb9942e2b1a1b54a6265b12b','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:12:18.623'),(2085927161818664962,'HTTP_REQUEST_DENIED','HTTP_REQUEST','9eab0a74-df9b-436a-abb8-3e0686b9ce12',NULL,'PLATFORM',NULL,NULL,'WEB','9eab0a74-df9b-436a-abb8-3e0686b9ce12','c40fb726579b4a519525d484782f5ac4','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:12:44.688'),(2085927166562422786,'HTTP_REQUEST_DENIED','HTTP_REQUEST','510c4753-6e47-4089-a1f8-63cde75001cf',NULL,'PLATFORM',NULL,NULL,'WEB','510c4753-6e47-4089-a1f8-63cde75001cf','ceb054d1e41b4173a0ec2cb562c54fec','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:12:45.819'),(2085927170626703361,'HTTP_REQUEST_DENIED','HTTP_REQUEST','4d7fc7a2-3a63-4521-9c77-aee6fb005b01',NULL,'PLATFORM',NULL,NULL,'WEB','4d7fc7a2-3a63-4521-9c77-aee6fb005b01','578849db21cc4c8bb57514e14cfa9772','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:12:46.788'),(2085927174175084546,'HTTP_REQUEST_DENIED','HTTP_REQUEST','66ce068b-86d1-44fc-a93d-87fb9c19b706',NULL,'PLATFORM',NULL,NULL,'WEB','66ce068b-86d1-44fc-a93d-87fb9c19b706','131f9852ee0243039f9d596e1a7cd04c','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:12:47.634'),(2085927177710882817,'HTTP_REQUEST_DENIED','HTTP_REQUEST','19aa7fbc-28a9-4968-886e-da36cb04c6d9',NULL,'PLATFORM',NULL,NULL,'WEB','19aa7fbc-28a9-4968-886e-da36cb04c6d9','10d55899731946009c8d0f6f2cf237c9','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:12:48.477'),(2085927182412697601,'HTTP_REQUEST_DENIED','HTTP_REQUEST','92e3d9ef-9059-4cc6-b5f6-c789e0b43683',NULL,'PLATFORM',NULL,NULL,'WEB','92e3d9ef-9059-4cc6-b5f6-c789e0b43683','816c6ffd196b4affb9f0459e5e8b4ed8','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:12:49.598'),(2085928242497593346,'HTTP_REQUEST_DENIED','HTTP_REQUEST','5276e0b9-f920-4d8f-8218-2759800b670f',NULL,'PLATFORM',NULL,NULL,'WEB','5276e0b9-f920-4d8f-8218-2759800b670f','f44cc3fea3964c9b93ca26f3980243c3','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:17:02.364'),(2085928726046318593,'HTTP_REQUEST_DENIED','HTTP_REQUEST','f4ec6c46-c1a8-4ba7-99c9-e7e980d8d451',NULL,'PLATFORM',NULL,NULL,'WEB','f4ec6c46-c1a8-4ba7-99c9-e7e980d8d451','fc8cb62e928b43d78ecfbbde2c59977c','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:18:57.630'),(2085928836427816961,'HTTP_REQUEST_DENIED','HTTP_REQUEST','9ca6ff05-4cce-4734-8273-71c56afd2802',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','9ca6ff05-4cce-4734-8273-71c56afd2802','a3076f40bb2b4440a06540bbad6acb19','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:19:23.946'),(2085928841196740609,'HTTP_REQUEST_DENIED','HTTP_REQUEST','bc9b5810-d44a-480d-b201-0d8a894ed6db',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','bc9b5810-d44a-480d-b201-0d8a894ed6db','7021bb1f7a0a4766a8c14719416f4c40','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:19:25.085'),(2085928843784626177,'HTTP_REQUEST_DENIED','HTTP_REQUEST','c1d72b61-d5fc-46c3-bf05-68c7fa07e13e',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','c1d72b61-d5fc-46c3-bf05-68c7fa07e13e','4323a688eaf44a39ab4dd53023a68c8a','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:19:25.700'),(2085928855381876738,'HTTP_REQUEST_DENIED','HTTP_REQUEST','39693f64-e644-4c79-aa8e-dfde3f2504a5',NULL,'PLATFORM',NULL,NULL,'WEB','39693f64-e644-4c79-aa8e-dfde3f2504a5','cbb11c2b1efb4f2d98ef3fc5520c270e','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:19:28.465'),(2085928924873105409,'HTTP_REQUEST_DENIED','HTTP_REQUEST','22e055ad-55a7-40ba-8f7e-579fbf96f90f',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','22e055ad-55a7-40ba-8f7e-579fbf96f90f','0e6ba15d494349198c2cc2890818ff6a','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:19:45.034'),(2085928939226013697,'HTTP_REQUEST_DENIED','HTTP_REQUEST','28fc5ca4-b3f7-437d-9f1d-a05f3b4c65c6',NULL,'PLATFORM',NULL,NULL,'WEB','28fc5ca4-b3f7-437d-9f1d-a05f3b4c65c6','88c9f24d0ef64a5abed47e7c83e6be11','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:19:48.455'),(2085928996662812674,'HTTP_REQUEST_DENIED','HTTP_REQUEST','bb341a07-ede8-4cce-a3e8-b9464d467fc6',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','bb341a07-ede8-4cce-a3e8-b9464d467fc6','fb8b5191ff0446f5a3c95e87d6eadef8','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:20:02.149'),(2085930009424048129,'HTTP_REQUEST_DENIED','HTTP_REQUEST','2edf3bef-5d57-4203-8aaa-177a674f7009',NULL,'PLATFORM',NULL,NULL,'WEB','2edf3bef-5d57-4203-8aaa-177a674f7009','727f03c9b40149ce9ed6bc9c12c7854b','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:03.627'),(2085930137073496065,'HTTP_REQUEST_DENIED','HTTP_REQUEST','78e09c77-3557-4922-a351-43cbf0e0dba7',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','78e09c77-3557-4922-a351-43cbf0e0dba7','10ca47b2eade4ea49a31201d5f24049d','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:24:34.045'),(2085930141678841858,'HTTP_REQUEST_DENIED','HTTP_REQUEST','0230a01d-e15d-4f9e-92d0-acb3d884367c',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','0230a01d-e15d-4f9e-92d0-acb3d884367c','ce502795dfa942c79d39d194d329e55c','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:24:35.143'),(2085930144111538178,'HTTP_REQUEST_DENIED','HTTP_REQUEST','78e938db-c373-4cba-89d5-7548b0828b9f',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','78e938db-c373-4cba-89d5-7548b0828b9f','7e2eaf38f00646619f8975a6cb682072','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:24:35.722'),(2085930154085593089,'HTTP_REQUEST_DENIED','HTTP_REQUEST','4b5fe4c4-fe24-4378-a6ad-6d538fdf1b8d',NULL,'PLATFORM',NULL,NULL,'WEB','4b5fe4c4-fe24-4378-a6ad-6d538fdf1b8d','18a3f80b19024a2e90f643944a87b71c','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:38.100'),(2085930159731126274,'HTTP_REQUEST_DENIED','HTTP_REQUEST','38cbbd6e-552d-4a4d-ba86-4099f886f888',NULL,'PLATFORM',NULL,NULL,'WEB','38cbbd6e-552d-4a4d-ba86-4099f886f888','1508045fe54040c186450ca3d75118f3','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:39.446'),(2085930164483272706,'HTTP_REQUEST_DENIED','HTTP_REQUEST','ea1f353a-bc27-459a-89fb-5b5c8e91531e',NULL,'PLATFORM',NULL,NULL,'WEB','ea1f353a-bc27-459a-89fb-5b5c8e91531e','a48e7d5781ad48f5bbdd9d1e1290b409','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:40.579'),(2085930168723714049,'HTTP_REQUEST_DENIED','HTTP_REQUEST','107f9878-abe5-4afa-b0db-11d871e26a8e',NULL,'PLATFORM',NULL,NULL,'WEB','107f9878-abe5-4afa-b0db-11d871e26a8e','df5bd667ceba449aa2a29c01c3ed3798','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:41.590'),(2085930172565696514,'HTTP_REQUEST_DENIED','HTTP_REQUEST','2b48446e-9a2b-455e-9fec-96cfe44502b8',NULL,'PLATFORM',NULL,NULL,'WEB','2b48446e-9a2b-455e-9fec-96cfe44502b8','a0aa8e203f2149918fbaae8da964393c','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:42.506'),(2085930176239906818,'HTTP_REQUEST_DENIED','HTTP_REQUEST','7b1bba2c-3410-447e-80dd-923ef5dc2b38',NULL,'PLATFORM',NULL,NULL,'WEB','7b1bba2c-3410-447e-80dd-923ef5dc2b38','b30a658d9cf04748b02f33c56c4684c4','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:43.383'),(2085930181386317825,'HTTP_REQUEST_DENIED','HTTP_REQUEST','f13fe136-da2a-40c2-a72d-d348e869d9e6',NULL,'PLATFORM',NULL,NULL,'WEB','f13fe136-da2a-40c2-a72d-d348e869d9e6','2a7ec4318ada41b5aab7b7d333d88862','DENIED','{\"path\": \"/api/v1/me/context\", \"method\": \"GET\"}',NULL,'AUTH_REQUIRED','2026-08-08 03:24:44.609'),(2085930417110396929,'HTTP_REQUEST_DENIED','HTTP_REQUEST','2d3ab513-ca53-45d4-8b63-7a0f2e4e3a51',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','2d3ab513-ca53-45d4-8b63-7a0f2e4e3a51','53e67e9eabcf4ccb80b9c7972af97281','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:25:40.810'),(2085931018682642434,'HTTP_REQUEST_DENIED','HTTP_REQUEST','7ba0e000-cb5f-422b-9bd5-bd6f1b90a23b',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','7ba0e000-cb5f-422b-9bd5-bd6f1b90a23b','2f4c385ba6954a9885883964808aaa6e','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:28:04.235'),(2085931021052424194,'HTTP_REQUEST_DENIED','HTTP_REQUEST','38ac3752-3be4-4753-afaf-cfc56111453c',2085917350597300225,'SYSTEM',2085920203721703426,2085920203797200898,'WEB','38ac3752-3be4-4753-afaf-cfc56111453c','5ad8be2dabc14c888a2ee7749d863a6d','DENIED','{\"path\": \"/api/v1/systems/2085920203721703426/runtime/navigation\", \"method\": \"GET\"}',NULL,'MODULE_NOT_PUBLISHED','2026-08-08 03:28:04.801');
/*!40000 ALTER TABLE `un_audit_operation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_audit_security`
--

DROP TABLE IF EXISTS `un_audit_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_audit_security` (
  `id` bigint NOT NULL,
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_id` bigint DEFAULT NULL,
  `account_hint` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `source_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `remote_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_security_account` (`account_id`,`created_at`),
  KEY `idx_audit_security_request` (`request_id`),
  KEY `idx_audit_security_created` (`created_at`),
  CONSTRAINT `ck_audit_security_result` CHECK ((`result` in (_utf8mb4'SUCCESS',_utf8mb4'DENIED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_audit_security`
--

LOCK TABLES `un_audit_security` WRITE;
/*!40000 ALTER TABLE `un_audit_security` DISABLE KEYS */;
INSERT INTO `un_audit_security` VALUES (2085919779962781698,'LOGIN',2085917350597300225,'c49a52efd8239989',NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36','11be07a2-eb5d-47dd-8454-d6183dac88f7','2af4f5410d354e6b84be771c8f951b25','SUCCESS',NULL,'{}','2026-08-08 02:43:24.716'),(2085920299347640321,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36','fc821c53-9cd7-4fe6-bc84-7836643a171f','8f82dea39e864aecad138a532dbd974c','SUCCESS',NULL,'{}','2026-08-08 02:45:28.547'),(2085923663592636418,'LOGIN',2085917350597300225,'c49a52efd8239989',NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','dca59670-a103-4636-bc9b-0035c6618848','c2def5f7a6014abd8c626919c38a6528','SUCCESS',NULL,'{}','2026-08-08 02:58:50.645'),(2085923785311338498,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','beb96afc-e693-48f7-8f74-4aecf2d3a1a1','25cb6c7a43614a028d908ecaf194853d','SUCCESS',NULL,'{}','2026-08-08 02:59:19.666'),(2085923885819445249,'CONTEXT_SWITCH_PLATFORM',2085917350597300225,NULL,NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','c61a449c-1e00-4d44-af0b-4857e90a66dc','8e519d3ce7f24c2e8d4629dfcf63bdd5','SUCCESS',NULL,'{}','2026-08-08 02:59:43.629'),(2085923894518431745,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','03563a5a-ac12-479b-8503-dc5635e99e61','c812776ad7754626abef3cdd1f2ea04a','SUCCESS',NULL,'{}','2026-08-08 02:59:45.703'),(2085926680035741697,'CONTEXT_SWITCH_PLATFORM',2085917350597300225,NULL,NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','4dfe512c-5446-4fe3-92b8-383711ae4cf5','07483567d7c846b68acfd60386a91405','SUCCESS',NULL,'{}','2026-08-08 03:10:49.822'),(2085926689330319362,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','790cb644-9c31-4243-b153-a909826af8c9','d610cab8ef6b4563ad39e02e8ce8b995','SUCCESS',NULL,'{}','2026-08-08 03:10:52.038'),(2085926942645309442,'CONTEXT_SWITCH_PLATFORM',2085917350597300225,NULL,NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','f0df8438-1f77-4737-bcd4-c5a2d6106279','f671216e28c94f78b1f5bc0a542c8ceb','SUCCESS',NULL,'{}','2026-08-08 03:11:52.433'),(2085926951658868737,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','8e537b0c-a437-4a1d-8fa2-4ea2acbb2dac','3cf6863923ce4ffda2975d51c90263e9','SUCCESS',NULL,'{}','2026-08-08 03:11:54.581'),(2085928402212495362,'LOGIN',2085917350597300225,'c49a52efd8239989',NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36','7394f8dd-16fc-422f-b66f-368faebfb867','307f42911dec4db1920efa3d04b852de','SUCCESS',NULL,'{}','2026-08-08 03:17:40.422'),(2085928730123182081,'LOGIN',2085917350597300225,'c49a52efd8239989',NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','2e1b1c1f-1468-441a-bcdb-786e2079341f','cdf775c7bd434b9591df062e56cf406a','SUCCESS',NULL,'{}','2026-08-08 03:18:58.601'),(2085928830757117953,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','332848cb-e279-45c9-84e4-8c6f4165708c','e2a78c99ed4a43ea82a10b0e32e6406e','SUCCESS',NULL,'{}','2026-08-08 03:19:22.594'),(2085928913548484610,'CONTEXT_SWITCH_PLATFORM',2085917350597300225,NULL,NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','a4a51f77-cfbe-430d-8136-84c90120a6e6','1c61960efdca4860a57c6c24416ec5b3','SUCCESS',NULL,'{}','2026-08-08 03:19:42.333'),(2085928923820335106,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','de91e543-0d6f-4220-9ee9-544d82164641','348701a96b31489cab7a562838114f45','SUCCESS',NULL,'{}','2026-08-08 03:19:44.782'),(2085928988374867970,'CONTEXT_SWITCH_PLATFORM',2085917350597300225,NULL,NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','ff9490c6-698a-4f56-8fe2-8d5da02d8cbf','3403b9c622834242896678e7c0304fea','SUCCESS',NULL,'{}','2026-08-08 03:20:00.173'),(2085928996184662018,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','35e85b89-e4db-4427-b5ff-63d67a37ef55','c30cb0165f7c427ebc54cdf014c29c95','SUCCESS',NULL,'{}','2026-08-08 03:20:02.035'),(2085930015329628161,'LOGIN',2085917350597300225,'c49a52efd8239989',NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','95aaf98c-3db3-467f-bf86-23352cf833b0','489a4d061c8e4cfeb7126743a5300749','SUCCESS',NULL,'{}','2026-08-08 03:24:05.018'),(2085930132115828738,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','5746b47c-2f48-4fa4-b6ec-9a1952c78a62','b528ebbd06c047959b47f834675e9eee','SUCCESS',NULL,'{}','2026-08-08 03:24:32.862'),(2085930271110868994,'LOGIN',2085917350597300225,'c49a52efd8239989',NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','13c173ee-fbe5-4d38-8aaa-c1a0870eecbe','973e54c492964e3dbc3831b0edc21028','SUCCESS',NULL,'{}','2026-08-08 03:25:06.002'),(2085930416191844354,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','8ddfaf0e-e7e2-4a78-be07-1da1dfe67db3','fab37751496d42979929c61f080cba49','SUCCESS',NULL,'{}','2026-08-08 03:25:40.591'),(2085930964102164481,'CONTEXT_SWITCH_PLATFORM',2085917350597300225,NULL,NULL,NULL,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','503d4f6e-9637-45c0-8b14-37137ff08c3f','64a2e0895c0a4e338432938b5c995448','SUCCESS',NULL,'{}','2026-08-08 03:27:51.223'),(2085931017839587330,'CONTEXT_SWITCH_SYSTEM',2085917350597300225,NULL,2085920203721703426,2085920203797200898,'WEB','172.18.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','09157b71-c5ea-4907-a3c9-3f8cf7c01031','2bae945952f140afa81a9ce1698148be','SUCCESS',NULL,'{}','2026-08-08 03:28:04.035');
/*!40000 ALTER TABLE `un_audit_security` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_collab_record_comment`
--

DROP TABLE IF EXISTS `un_collab_record_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_collab_record_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `parent_comment_id` bigint DEFAULT NULL,
  `author_member_id` bigint NOT NULL,
  `body` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '1',
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`comment_id`),
  UNIQUE KEY `uk_collab_comment_scope` (`system_id`,`tenant_id`,`record_id`,`comment_id`),
  UNIQUE KEY `uk_collab_comment_idempotency` (`system_id`,`tenant_id`,`record_id`,`author_member_id`,`idempotency_key`),
  KEY `idx_collab_comment_page` (`system_id`,`tenant_id`,`record_id`,`created_at`,`comment_id`),
  KEY `idx_collab_comment_parent` (`system_id`,`tenant_id`,`record_id`,`parent_comment_id`,`created_at`,`comment_id`),
  KEY `fk_collab_comment_author` (`system_id`,`author_member_id`,`tenant_id`),
  KEY `fk_collab_comment_created_by` (`system_id`,`created_by`,`tenant_id`),
  KEY `fk_collab_comment_updated_by` (`system_id`,`updated_by`,`tenant_id`),
  KEY `fk_collab_comment_deleted_by` (`system_id`,`deleted_by`,`tenant_id`),
  CONSTRAINT `fk_collab_comment_author` FOREIGN KEY (`system_id`, `author_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_comment_created_by` FOREIGN KEY (`system_id`, `created_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_comment_deleted_by` FOREIGN KEY (`system_id`, `deleted_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_comment_parent` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `parent_comment_id`) REFERENCES `un_collab_record_comment` (`system_id`, `tenant_id`, `record_id`, `comment_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_comment_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_comment_updated_by` FOREIGN KEY (`system_id`, `updated_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_collab_comment_body` CHECK ((((`deleted` = 0) and (`body` is not null) and (char_length(trim(`body`)) between 1 and 4000)) or ((`deleted` = 1) and (`body` is null)))),
  CONSTRAINT `ck_collab_comment_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `ck_collab_comment_tombstone` CHECK ((((`deleted` = 0) and (`deleted_at` is null) and (`deleted_by` is null)) or ((`deleted` = 1) and (`deleted_at` is not null) and (`deleted_by` is not null)))),
  CONSTRAINT `ck_collab_comment_version` CHECK ((`version` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_collab_record_comment`
--

LOCK TABLES `un_collab_record_comment` WRITE;
/*!40000 ALTER TABLE `un_collab_record_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_collab_record_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_collab_record_comment_mention`
--

DROP TABLE IF EXISTS `un_collab_record_comment_mention`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_collab_record_comment_mention` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  `mentioned_member_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`record_id`,`comment_id`,`mentioned_member_id`),
  KEY `idx_collab_comment_mention_recipient` (`system_id`,`tenant_id`,`mentioned_member_id`,`created_at`,`comment_id`),
  KEY `fk_collab_comment_mention_team_member` (`system_id`,`tenant_id`,`record_id`,`mentioned_member_id`),
  KEY `fk_collab_comment_mention_created_by` (`system_id`,`created_by`,`tenant_id`),
  CONSTRAINT `fk_collab_comment_mention_comment` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `comment_id`) REFERENCES `un_collab_record_comment` (`system_id`, `tenant_id`, `record_id`, `comment_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_comment_mention_created_by` FOREIGN KEY (`system_id`, `created_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_comment_mention_team_member` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `mentioned_member_id`) REFERENCES `un_collab_record_team_member` (`system_id`, `tenant_id`, `record_id`, `member_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_collab_comment_mention_no_self` CHECK ((`mentioned_member_id` <> `created_by`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_collab_record_comment_mention`
--

LOCK TABLES `un_collab_record_comment_mention` WRITE;
/*!40000 ALTER TABLE `un_collab_record_comment_mention` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_collab_record_comment_mention` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_collab_record_team`
--

DROP TABLE IF EXISTS `un_collab_record_team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_collab_record_team` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`record_id`),
  KEY `idx_collab_team_updated` (`system_id`,`tenant_id`,`updated_at`,`record_id`),
  KEY `fk_collab_team_created_by` (`system_id`,`created_by`,`tenant_id`),
  KEY `fk_collab_team_updated_by` (`system_id`,`updated_by`,`tenant_id`),
  CONSTRAINT `fk_collab_team_created_by` FOREIGN KEY (`system_id`, `created_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_team_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_team_updated_by` FOREIGN KEY (`system_id`, `updated_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_collab_team_version` CHECK ((`version` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_collab_record_team`
--

LOCK TABLES `un_collab_record_team` WRITE;
/*!40000 ALTER TABLE `un_collab_record_team` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_collab_record_team` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_collab_record_team_member`
--

DROP TABLE IF EXISTS `un_collab_record_team_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_collab_record_team_member` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `team_role` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_slot` tinyint GENERATED ALWAYS AS ((case when (`team_role` = _ascii'OWNER') then 1 else NULL end)) STORED,
  `row_version` bigint NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`record_id`,`member_id`),
  UNIQUE KEY `uk_collab_team_member` (`system_id`,`tenant_id`,`record_id`,`member_id`),
  UNIQUE KEY `uk_collab_team_single_owner` (`system_id`,`tenant_id`,`record_id`,`owner_slot`),
  KEY `idx_collab_team_member_lookup` (`system_id`,`tenant_id`,`member_id`,`team_role`,`record_id`),
  KEY `idx_collab_team_role` (`system_id`,`tenant_id`,`record_id`,`team_role`,`member_id`),
  KEY `fk_collab_team_member_access` (`system_id`,`member_id`,`tenant_id`),
  KEY `fk_collab_team_member_created_by` (`system_id`,`created_by`,`tenant_id`),
  KEY `fk_collab_team_member_updated_by` (`system_id`,`updated_by`,`tenant_id`),
  CONSTRAINT `fk_collab_team_member_access` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_team_member_created_by` FOREIGN KEY (`system_id`, `created_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_team_member_team` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`) REFERENCES `un_collab_record_team` (`system_id`, `tenant_id`, `record_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collab_team_member_updated_by` FOREIGN KEY (`system_id`, `updated_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_collab_team_member_version` CHECK ((`row_version` >= 1)),
  CONSTRAINT `ck_collab_team_owner_slot` CHECK ((((`team_role` = _utf8mb4'OWNER') and (`owner_slot` = 1)) or ((`team_role` <> _utf8mb4'OWNER') and (`owner_slot` is null)))),
  CONSTRAINT `ck_collab_team_role` CHECK ((`team_role` in (_utf8mb4'OWNER',_utf8mb4'COLLABORATOR',_utf8mb4'VIEWER',_utf8mb4'FOLLOWER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_collab_record_team_member`
--

LOCK TABLES `un_collab_record_team_member` WRITE;
/*!40000 ALTER TABLE `un_collab_record_team_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_collab_record_team_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_event_channel_configuration`
--

DROP TABLE IF EXISTS `un_event_channel_configuration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_event_channel_configuration` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `endpoint` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `timeout_ms` int NOT NULL DEFAULT '5000',
  `last_check_at` datetime(3) DEFAULT NULL,
  `last_check_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_check_trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_check_duration_ms` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_channel_configuration` (`system_id`,`channel`),
  KEY `idx_event_channel_configuration_enabled` (`system_id`,`enabled`,`channel`),
  CONSTRAINT `fk_event_channel_configuration_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_event_channel_configuration_channel` CHECK ((`channel` in (_utf8mb4'EMAIL',_utf8mb4'WEBHOOK'))),
  CONSTRAINT `ck_event_channel_configuration_check_status` CHECK (((`last_check_status` is null) or (`last_check_status` in (_utf8mb4'SENT',_utf8mb4'TEMPORARY_FAILURE',_utf8mb4'PERMANENT_FAILURE')))),
  CONSTRAINT `ck_event_channel_configuration_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_event_channel_configuration_shape` CHECK ((((`channel` = _utf8mb4'EMAIL') and (`endpoint` is null) and (`secret_ref` is null)) or (`channel` = _utf8mb4'WEBHOOK'))),
  CONSTRAINT `ck_event_channel_configuration_timeout` CHECK ((`timeout_ms` between 100 and 30000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_event_channel_configuration`
--

LOCK TABLES `un_event_channel_configuration` WRITE;
/*!40000 ALTER TABLE `un_event_channel_configuration` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_event_channel_configuration` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_event_delivery_preference`
--

DROP TABLE IF EXISTS `un_event_delivery_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_event_delivery_preference` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `template_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_delivery_preference_owner` (`system_id`,`tenant_id`,`member_id`,`template_code`,`channel`),
  KEY `idx_event_delivery_preference_member_tenant` (`system_id`,`member_id`,`tenant_id`),
  KEY `idx_event_delivery_preference_template` (`system_id`,`template_code`),
  CONSTRAINT `fk_event_delivery_preference_member_tenant` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`),
  CONSTRAINT `fk_event_delivery_preference_template` FOREIGN KEY (`system_id`, `template_code`) REFERENCES `un_event_message_template` (`system_id`, `template_code`),
  CONSTRAINT `ck_event_delivery_preference_channel` CHECK ((`channel` in (_utf8mb4'INBOX',_utf8mb4'EMAIL',_utf8mb4'WEBHOOK'))),
  CONSTRAINT `ck_event_delivery_preference_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_event_delivery_preference_scope` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`member_id` > 0))),
  CONSTRAINT `ck_event_delivery_preference_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_event_delivery_preference`
--

LOCK TABLES `un_event_delivery_preference` WRITE;
/*!40000 ALTER TABLE `un_event_delivery_preference` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_event_delivery_preference` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_event_message`
--

DROP TABLE IF EXISTS `un_event_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_event_message` (
  `id` bigint unsigned NOT NULL,
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `sender_member_id` bigint unsigned NOT NULL,
  `recipient_member_id` bigint unsigned NOT NULL,
  `template_code` varchar(100) NOT NULL,
  `title` varchar(200) NOT NULL,
  `body` varchar(4000) NOT NULL,
  `target_type` varchar(64) DEFAULT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `target_path` varchar(500) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `archived_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_message_scope_id` (`system_id`,`tenant_id`,`id`),
  KEY `idx_event_message_inbox_state` (`system_id`,`tenant_id`,`recipient_member_id`,`status`,`created_at`),
  KEY `idx_event_message_template_created` (`system_id`,`tenant_id`,`template_code`,`created_at`),
  CONSTRAINT `chk_event_message_members` CHECK (((`sender_member_id` > 0) and (`recipient_member_id` > 0))),
  CONSTRAINT `chk_event_message_scope` CHECK (((`system_id` > 0) and (`tenant_id` > 0))),
  CONSTRAINT `chk_event_message_state` CHECK ((((`status` = _utf8mb4'UNREAD') and (`read_at` is null) and (`archived_at` is null)) or ((`status` = _utf8mb4'READ') and (`read_at` is not null) and (`archived_at` is null)) or ((`status` = _utf8mb4'ARCHIVED') and (`read_at` is not null) and (`archived_at` is not null)))),
  CONSTRAINT `chk_event_message_status` CHECK ((`status` in (_utf8mb4'UNREAD',_utf8mb4'READ',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `chk_event_message_target` CHECK ((((`target_type` is null) and (`target_id` is null)) or ((`target_type` is not null) and (`target_id` is not null)))),
  CONSTRAINT `chk_event_message_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_event_message`
--

LOCK TABLES `un_event_message` WRITE;
/*!40000 ALTER TABLE `un_event_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_event_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_event_message_delivery_attempt`
--

DROP TABLE IF EXISTS `un_event_message_delivery_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_event_message_delivery_attempt` (
  `id` bigint NOT NULL,
  `delivery_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `attempt_no` int NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(3) NOT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_delivery_attempt` (`delivery_id`,`attempt_no`),
  KEY `idx_event_delivery_attempt_scope` (`system_id`,`tenant_id`,`delivery_id`,`attempt_no`),
  CONSTRAINT `fk_event_delivery_attempt_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `un_event_message_delivery_log` (`id`),
  CONSTRAINT `ck_event_delivery_attempt_duration` CHECK (((`duration_ms` is null) or (`duration_ms` >= 0))),
  CONSTRAINT `ck_event_delivery_attempt_no` CHECK ((`attempt_no` > 0)),
  CONSTRAINT `ck_event_delivery_attempt_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'DELIVERED',_utf8mb4'SKIPPED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_event_message_delivery_attempt`
--

LOCK TABLES `un_event_message_delivery_attempt` WRITE;
/*!40000 ALTER TABLE `un_event_message_delivery_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_event_message_delivery_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_event_message_delivery_log`
--

DROP TABLE IF EXISTS `un_event_message_delivery_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_event_message_delivery_log` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `recipient_member_id` bigint NOT NULL,
  `template_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_version_id` bigint DEFAULT NULL,
  `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dedupe_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '1',
  `message_id` bigint DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `masked_destination` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_delivery_dedupe` (`system_id`,`tenant_id`,`recipient_member_id`,`channel`,`dedupe_key`),
  UNIQUE KEY `uk_event_delivery_message` (`message_id`),
  KEY `idx_event_delivery_template_status` (`system_id`,`template_code`,`status`,`created_at`),
  KEY `idx_event_delivery_admin` (`system_id`,`tenant_id`,`channel`,`status`,`template_code`,`created_at`,`id`),
  CONSTRAINT `ck_event_delivery_attempt` CHECK ((`attempt_count` > 0)),
  CONSTRAINT `ck_event_delivery_channel` CHECK ((`channel` in (_utf8mb4'INBOX',_utf8mb4'EMAIL',_utf8mb4'WEBHOOK'))),
  CONSTRAINT `ck_event_delivery_duration` CHECK (((`duration_ms` is null) or (`duration_ms` >= 0))),
  CONSTRAINT `ck_event_delivery_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'DELIVERED',_utf8mb4'SKIPPED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_event_message_delivery_log`
--

LOCK TABLES `un_event_message_delivery_log` WRITE;
/*!40000 ALTER TABLE `un_event_message_delivery_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_event_message_delivery_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_event_message_template`
--

DROP TABLE IF EXISTS `un_event_message_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_event_message_template` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `template_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `desired_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `draft_title_template` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `draft_body_template` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `channels_json` json NOT NULL,
  `allowed_variables_json` json NOT NULL,
  `published_version_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_template_system_code` (`system_id`,`template_code`),
  UNIQUE KEY `uk_event_template_system_id` (`system_id`,`id`),
  CONSTRAINT `ck_event_template_enabled` CHECK ((`desired_enabled` in (0,1))),
  CONSTRAINT `ck_event_template_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_event_message_template`
--

LOCK TABLES `un_event_message_template` WRITE;
/*!40000 ALTER TABLE `un_event_message_template` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_event_message_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_event_message_template_version`
--

DROP TABLE IF EXISTS `un_event_message_template_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_event_message_template_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `template_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_no` bigint NOT NULL,
  `source_draft_version` bigint NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `title_template` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `body_template` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `channels_json` json NOT NULL,
  `allowed_variables_json` json NOT NULL,
  `published_at` datetime(3) NOT NULL,
  `published_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_template_version_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_event_template_version_no` (`template_id`,`version_no`),
  UNIQUE KEY `uk_event_template_source_draft` (`template_id`,`source_draft_version`),
  KEY `fk_event_template_version_template` (`system_id`,`template_id`),
  CONSTRAINT `fk_event_template_version_template` FOREIGN KEY (`system_id`, `template_id`) REFERENCES `un_event_message_template` (`system_id`, `id`),
  CONSTRAINT `ck_event_template_version_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_event_template_version_numbers` CHECK (((`version_no` > 0) and (`source_draft_version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_event_message_template_version`
--

LOCK TABLES `un_event_message_template_version` WRITE;
/*!40000 ALTER TABLE `un_event_message_template_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_event_message_template_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_file_object`
--

DROP TABLE IF EXISTS `un_file_object`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_file_object` (
  `id` bigint unsigned NOT NULL,
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `uploader_member_id` bigint unsigned NOT NULL,
  `object_key` varchar(300) NOT NULL,
  `original_name` varchar(255) NOT NULL,
  `media_type` varchar(150) NOT NULL,
  `size_bytes` bigint unsigned NOT NULL,
  `sha256` char(64) NOT NULL,
  `status` varchar(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_scope_id` (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_file_object_key` (`object_key`),
  KEY `idx_file_object_uploader_created` (`system_id`,`tenant_id`,`uploader_member_id`,`created_at`),
  KEY `idx_file_object_sha256` (`system_id`,`tenant_id`,`sha256`),
  KEY `idx_file_object_scope_status_created` (`system_id`,`tenant_id`,`status`,`created_at`,`id`),
  CONSTRAINT `chk_file_object_scope` CHECK (((`system_id` > 0) and (`tenant_id` > 0))),
  CONSTRAINT `chk_file_object_sha256` CHECK ((char_length(`sha256`) = 64)),
  CONSTRAINT `chk_file_object_size` CHECK ((`size_bytes` >= 0)),
  CONSTRAINT `chk_file_object_status` CHECK ((`status` = _utf8mb4'ACTIVE')),
  CONSTRAINT `chk_file_object_uploader` CHECK ((`uploader_member_id` > 0)),
  CONSTRAINT `chk_file_object_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_file_object`
--

LOCK TABLES `un_file_object` WRITE;
/*!40000 ALTER TABLE `un_file_object` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_file_object` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_file_reference`
--

DROP TABLE IF EXISTS `un_file_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_file_reference` (
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `file_id` bigint unsigned NOT NULL,
  `target_type` varchar(64) NOT NULL,
  `target_id` varchar(64) NOT NULL,
  `created_by_member_id` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`file_id`,`target_type`,`target_id`),
  KEY `idx_file_reference_target` (`system_id`,`tenant_id`,`target_type`,`target_id`,`file_id`),
  KEY `idx_file_reference_field_target` (`system_id`,`tenant_id`,`target_type`,`target_id`,`created_at`,`file_id`),
  CONSTRAINT `fk_file_reference_object` FOREIGN KEY (`system_id`, `tenant_id`, `file_id`) REFERENCES `un_file_object` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_file_reference_creator` CHECK ((`created_by_member_id` > 0)),
  CONSTRAINT `chk_file_reference_scope` CHECK (((`system_id` > 0) and (`tenant_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_file_reference`
--

LOCK TABLES `un_file_reference` WRITE;
/*!40000 ALTER TABLE `un_file_reference` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_file_reference` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_ai_definition_draft_execution`
--

DROP TABLE IF EXISTS `un_flow_ai_definition_draft_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_ai_definition_draft_execution` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `proposal_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `turn_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `operation` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `prepare_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `prepare_trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execute_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execute_trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_json` mediumtext,
  `created_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_flow_ai_definition_execution_id` (`id`),
  UNIQUE KEY `uk_flow_ai_definition_execution_proposal` (`system_id`,`tenant_id`,`member_id`,`proposal_id`),
  UNIQUE KEY `uk_flow_ai_definition_execution_key` (`system_id`,`tenant_id`,`member_id`,`idempotency_key`),
  KEY `fk_flow_ai_definition_execution_member` (`system_id`,`member_id`,`tenant_id`),
  CONSTRAINT `fk_flow_ai_definition_execution_member` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_ai_definition_execution_hash` CHECK (regexp_like(`payload_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_flow_ai_definition_execution_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`member_id` > 0) and (`account_id` > 0) and (`authorization_epoch` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0))),
  CONSTRAINT `ck_flow_ai_definition_execution_operation` CHECK ((`operation` = _utf8mb4'FLOW_DEFINITION_DRAFT')),
  CONSTRAINT `ck_flow_ai_definition_execution_result` CHECK (((((`result_json` is null) and (`completed_at` is null)) or ((`result_json` is not null) and (`completed_at` is not null))) and ((`result_json` is null) or json_valid(`result_json`)))),
  CONSTRAINT `ck_flow_ai_definition_execution_time` CHECK (((`expires_at` > `created_at`) and ((`completed_at` is null) or (`completed_at` >= `created_at`)))),
  CONSTRAINT `ck_flow_ai_definition_execution_tokens` CHECK ((regexp_like(`proposal_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`session_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`turn_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`idempotency_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_ai_definition_draft_execution`
--

LOCK TABLES `un_flow_ai_definition_draft_execution` WRITE;
/*!40000 ALTER TABLE `un_flow_ai_definition_draft_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_ai_definition_draft_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_approval_delegation`
--

DROP TABLE IF EXISTS `un_flow_approval_delegation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_approval_delegation` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `delegation_id` bigint NOT NULL,
  `delegator_member_id` bigint NOT NULL,
  `delegate_member_id` bigint NOT NULL,
  `definition_id` bigint DEFAULT NULL,
  `starts_at` datetime(6) NOT NULL,
  `ends_at` datetime(6) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `revoked_by` bigint DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`delegation_id`),
  KEY `idx_flow_delegation_outgoing` (`system_id`,`tenant_id`,`delegator_member_id`,`status`,`starts_at`,`ends_at`,`delegation_id`),
  KEY `idx_flow_delegation_incoming` (`system_id`,`tenant_id`,`delegate_member_id`,`status`,`starts_at`,`ends_at`,`delegation_id`),
  KEY `idx_flow_delegation_definition` (`system_id`,`tenant_id`,`definition_id`,`status`,`starts_at`,`ends_at`,`delegation_id`),
  KEY `fk_flow_delegation_delegator` (`system_id`,`delegator_member_id`,`tenant_id`),
  KEY `fk_flow_delegation_delegate` (`system_id`,`delegate_member_id`,`tenant_id`),
  KEY `fk_flow_delegation_creator` (`system_id`,`created_by`,`tenant_id`),
  KEY `fk_flow_delegation_revoker` (`system_id`,`revoked_by`,`tenant_id`),
  CONSTRAINT `fk_flow_delegation_creator` FOREIGN KEY (`system_id`, `created_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_delegation_definition` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`) REFERENCES `un_flow_definition_draft` (`system_id`, `tenant_id`, `definition_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_delegation_delegate` FOREIGN KEY (`system_id`, `delegate_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_delegation_delegator` FOREIGN KEY (`system_id`, `delegator_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_delegation_revoker` FOREIGN KEY (`system_id`, `revoked_by`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_delegation_identity` CHECK (((`delegation_id` > 0) and (`delegator_member_id` > 0) and (`delegate_member_id` > 0) and (`delegator_member_id` <> `delegate_member_id`) and (`created_by` > 0) and ((`definition_id` is null) or (`definition_id` > 0)))),
  CONSTRAINT `ck_flow_delegation_revocation` CHECK ((((`status` in (_utf8mb4'SCHEDULED',_utf8mb4'ACTIVE',_utf8mb4'EXPIRED')) and (`revoked_by` is null) and (`revoked_at` is null)) or ((`status` = _utf8mb4'REVOKED') and (`revoked_by` is not null) and (`revoked_at` is not null) and (`revoked_at` >= `created_at`)))),
  CONSTRAINT `ck_flow_delegation_status` CHECK ((`status` in (_utf8mb4'SCHEDULED',_utf8mb4'ACTIVE',_utf8mb4'EXPIRED',_utf8mb4'REVOKED'))),
  CONSTRAINT `ck_flow_delegation_window` CHECK (((`ends_at` > `starts_at`) and (`ends_at` <= (`starts_at` + interval 180 day))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_approval_delegation`
--

LOCK TABLES `un_flow_approval_delegation` WRITE;
/*!40000 ALTER TABLE `un_flow_approval_delegation` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_approval_delegation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_comment`
--

DROP TABLE IF EXISTS `un_flow_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_comment` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `body` varchar(2000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`comment_id`),
  KEY `idx_flow_comment_instance_page` (`system_id`,`tenant_id`,`instance_id`,`created_at`,`comment_id`),
  CONSTRAINT `fk_flow_comment_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_comment_body` CHECK (((`body` = trim(`body`)) and (char_length(`body`) between 1 and 2000))),
  CONSTRAINT `ck_flow_comment_identity` CHECK (((`comment_id` > 0) and (`author_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_comment`
--

LOCK TABLES `un_flow_comment` WRITE;
/*!40000 ALTER TABLE `un_flow_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_compensation_attempt`
--

DROP TABLE IF EXISTS `un_flow_compensation_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_compensation_attempt` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `attempt_id` bigint NOT NULL,
  `compensation_id` bigint NOT NULL,
  `attempt_number` int unsigned NOT NULL,
  `event_sequence` int unsigned NOT NULL,
  `event_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_member_id` bigint DEFAULT NULL,
  `lease_owner` varchar(160) DEFAULT NULL,
  `idempotency_key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `failure_message` varchar(500) DEFAULT NULL,
  `http_status` smallint unsigned DEFAULT NULL,
  `duration_ms` bigint unsigned DEFAULT NULL,
  `response_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`attempt_id`),
  UNIQUE KEY `uk_flow_compensation_attempt_sequence` (`system_id`,`tenant_id`,`compensation_id`,`event_sequence`),
  UNIQUE KEY `uk_flow_compensation_attempt_idempotency` (`system_id`,`tenant_id`,`compensation_id`,`idempotency_key_hash`),
  KEY `idx_flow_compensation_attempt` (`system_id`,`tenant_id`,`compensation_id`,`occurred_at`,`attempt_id`),
  CONSTRAINT `fk_flow_compensation_attempt` FOREIGN KEY (`system_id`, `tenant_id`, `compensation_id`) REFERENCES `un_flow_completion_compensation` (`system_id`, `tenant_id`, `compensation_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_compensation_attempt_event` CHECK ((`event_type` in (_utf8mb4'ACTIVATED',_utf8mb4'STARTED',_utf8mb4'STAGE_JOINED',_utf8mb4'CLAIMED',_utf8mb4'LEASE_EXPIRED',_utf8mb4'RETRIED',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_flow_compensation_attempt_identity` CHECK (((`attempt_id` > 0) and (`compensation_id` > 0) and (`event_sequence` > 0) and ((`actor_member_id` is null) or (`actor_member_id` > 0)))),
  CONSTRAINT `ck_flow_compensation_attempt_values` CHECK ((((`lease_owner` is null) or (char_length(trim(`lease_owner`)) between 1 and 160)) and ((`idempotency_key_hash` is null) or regexp_like(`idempotency_key_hash`,_utf8mb4'^[0-9a-f]{64}$')) and ((`result_json` is null) or ((json_type(`result_json`) = _utf8mb4'OBJECT') and (length(`result_json`) <= 8192))) and (((`failure_code` is null) and (`failure_message` is null)) or (regexp_like(`failure_code`,_utf8mb4'^[A-Z][A-Z0-9_]{0,63}$') and (char_length(trim(`failure_message`)) between 1 and 500))) and ((`http_status` is null) or (`http_status` between 100 and 599)) and ((`duration_ms` is null) or (`duration_ms` <= 86400000)) and ((`response_sha256` is null) or regexp_like(`response_sha256`,_utf8mb4'^[0-9a-f]{64}$'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_compensation_attempt`
--

LOCK TABLES `un_flow_compensation_attempt` WRITE;
/*!40000 ALTER TABLE `un_flow_compensation_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_compensation_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_compensation_subflow_run`
--

DROP TABLE IF EXISTS `un_flow_compensation_subflow_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_compensation_subflow_run` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `compensation_subflow_run_id` bigint NOT NULL,
  `compensation_id` bigint NOT NULL,
  `attempt_number` int unsigned NOT NULL,
  `launch_key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `child_instance_id` bigint NOT NULL,
  `target_definition_id` bigint NOT NULL,
  `target_definition_version` int unsigned NOT NULL,
  `root_instance_id` bigint NOT NULL,
  `subflow_depth` tinyint unsigned NOT NULL,
  `child_status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `launched_at` datetime(6) NOT NULL,
  `terminal_at` datetime(6) DEFAULT NULL,
  `result_code` varchar(24) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_applied_at` datetime(6) DEFAULT NULL,
  `state_version` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`system_id`,`tenant_id`,`compensation_subflow_run_id`),
  UNIQUE KEY `uk_flow_compensation_subflow_attempt` (`system_id`,`tenant_id`,`compensation_id`,`attempt_number`),
  UNIQUE KEY `uk_flow_compensation_subflow_launch` (`system_id`,`tenant_id`,`launch_key`),
  UNIQUE KEY `uk_flow_compensation_subflow_child` (`system_id`,`tenant_id`,`child_instance_id`),
  KEY `idx_flow_compensation_subflow_result` (`system_id`,`tenant_id`,`result_applied_at`,`terminal_at`,`compensation_subflow_run_id`),
  KEY `fk_flow_compensation_subflow_root` (`system_id`,`tenant_id`,`root_instance_id`),
  KEY `fk_flow_compensation_subflow_target` (`system_id`,`tenant_id`,`target_definition_id`,`target_definition_version`),
  CONSTRAINT `fk_flow_compensation_subflow_child` FOREIGN KEY (`system_id`, `tenant_id`, `child_instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_compensation_subflow_compensation` FOREIGN KEY (`system_id`, `tenant_id`, `compensation_id`) REFERENCES `un_flow_completion_compensation` (`system_id`, `tenant_id`, `compensation_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_compensation_subflow_root` FOREIGN KEY (`system_id`, `tenant_id`, `root_instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_compensation_subflow_target` FOREIGN KEY (`system_id`, `tenant_id`, `target_definition_id`, `target_definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_compensation_subflow_identity` CHECK (((`compensation_subflow_run_id` > 0) and (`compensation_id` > 0) and (`attempt_number` > 0) and regexp_like(`launch_key`,_utf8mb4'^[0-9a-f]{64}$') and (`child_instance_id` > 0) and (`target_definition_id` > 0) and (`target_definition_version` > 0) and (`root_instance_id` > 0) and (`subflow_depth` between 1 and 8))),
  CONSTRAINT `ck_flow_compensation_subflow_result` CHECK (((`child_status` in (_utf8mb4'RUNNING',_utf8mb4'APPROVED_COMPLETED',_utf8mb4'REJECTED',_utf8mb4'WITHDRAWN',_utf8mb4'TERMINATED')) and (((`child_status` = _utf8mb4'RUNNING') and (`terminal_at` is null) and (`result_code` is null) and (`result_applied_at` is null)) or ((`child_status` <> _utf8mb4'RUNNING') and (`terminal_at` is not null) and (`terminal_at` >= `launched_at`) and (`result_code` = `child_status`) and ((`result_applied_at` is null) or (`result_applied_at` >= `terminal_at`))))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_compensation_subflow_run`
--

LOCK TABLES `un_flow_compensation_subflow_run` WRITE;
/*!40000 ALTER TABLE `un_flow_compensation_subflow_run` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_compensation_subflow_run` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_completion_attempt`
--

DROP TABLE IF EXISTS `un_flow_completion_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_completion_attempt` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `attempt_id` bigint NOT NULL,
  `execution_id` bigint NOT NULL,
  `attempt_number` int unsigned NOT NULL,
  `event_sequence` int unsigned NOT NULL,
  `event_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_member_id` bigint DEFAULT NULL,
  `lease_owner` varchar(160) DEFAULT NULL,
  `idempotency_key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `failure_message` varchar(500) DEFAULT NULL,
  `http_status` smallint unsigned DEFAULT NULL,
  `duration_ms` bigint unsigned DEFAULT NULL,
  `response_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`attempt_id`),
  UNIQUE KEY `uk_flow_completion_attempt_sequence` (`system_id`,`tenant_id`,`execution_id`,`event_sequence`),
  UNIQUE KEY `uk_flow_completion_attempt_idempotency` (`system_id`,`tenant_id`,`execution_id`,`idempotency_key_hash`),
  KEY `idx_flow_completion_attempt_execution` (`system_id`,`tenant_id`,`execution_id`,`occurred_at`,`attempt_id`),
  CONSTRAINT `fk_flow_completion_attempt_execution` FOREIGN KEY (`system_id`, `tenant_id`, `execution_id`) REFERENCES `un_flow_completion_execution` (`system_id`, `tenant_id`, `execution_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_completion_attempt_event` CHECK ((`event_type` in (_utf8mb4'ACTIVATED',_utf8mb4'STARTED',_utf8mb4'STAGE_JOINED',_utf8mb4'CLAIMED',_utf8mb4'LEASE_EXPIRED',_utf8mb4'RETRIED',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_flow_completion_attempt_identity` CHECK (((`attempt_id` > 0) and (`execution_id` > 0) and (`event_sequence` > 0) and ((`actor_member_id` is null) or (`actor_member_id` > 0)))),
  CONSTRAINT `ck_flow_completion_attempt_values` CHECK ((((`lease_owner` is null) or (char_length(trim(`lease_owner`)) between 1 and 160)) and ((`idempotency_key_hash` is null) or regexp_like(`idempotency_key_hash`,_ascii'^[0-9a-f]{64}$')) and ((`result_json` is null) or ((json_type(`result_json`) = _utf8mb4'OBJECT') and (length(`result_json`) <= 8192))) and (((`failure_code` is null) and (`failure_message` is null)) or (regexp_like(`failure_code`,_ascii'^[A-Z][A-Z0-9_]{0,63}$') and (`failure_message` is not null) and (char_length(trim(`failure_message`)) between 1 and 500))) and ((`http_status` is null) or (`http_status` between 100 and 599)) and ((`duration_ms` is null) or (`duration_ms` <= 86400000)) and ((`response_sha256` is null) or regexp_like(`response_sha256`,_ascii'^[0-9a-f]{64}$')) and (((`http_status` is null) and (`duration_ms` is null) and (`response_sha256` is null) and (`started_at` is null) and (`completed_at` is null)) or ((`event_type` in (_ascii'SUCCEEDED',_ascii'FAILED',_ascii'RETRIED')) and (`duration_ms` is not null) and (`started_at` is not null) and (`completed_at` is not null) and (`completed_at` >= `started_at`)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_completion_attempt`
--

LOCK TABLES `un_flow_completion_attempt` WRITE;
/*!40000 ALTER TABLE `un_flow_completion_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_completion_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_completion_compensation`
--

DROP TABLE IF EXISTS `un_flow_completion_compensation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_completion_compensation` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `compensation_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `original_execution_id` bigint NOT NULL,
  `original_ordinal` tinyint unsigned NOT NULL,
  `reverse_ordinal` tinyint unsigned NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `step_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `step_name` varchar(80) NOT NULL,
  `execution_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `config_json` json NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT '0',
  `state_version` int unsigned NOT NULL DEFAULT '0',
  `available_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(160) DEFAULT NULL,
  `lease_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `terminal_at` datetime(6) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `failure_message` varchar(500) DEFAULT NULL,
  `failure_retryable` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`compensation_id`),
  UNIQUE KEY `uk_flow_compensation_original` (`system_id`,`tenant_id`,`instance_id`,`original_execution_id`),
  UNIQUE KEY `uk_flow_compensation_reverse` (`system_id`,`tenant_id`,`instance_id`,`reverse_ordinal`),
  KEY `idx_flow_compensation_lock_order` (`system_id`,`tenant_id`,`instance_id`,`original_ordinal`,`compensation_id`),
  KEY `idx_flow_compensation_due` (`system_id`,`tenant_id`,`execution_type`,`status`,`available_at`,`compensation_id`),
  KEY `idx_flow_compensation_lease_due` (`system_id`,`tenant_id`,`status`,`lease_expires_at`,`execution_type`,`compensation_id`),
  KEY `fk_flow_compensation_original` (`system_id`,`tenant_id`,`original_execution_id`),
  KEY `fk_flow_compensation_version` (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  CONSTRAINT `fk_flow_compensation_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_compensation_original` FOREIGN KEY (`system_id`, `tenant_id`, `original_execution_id`) REFERENCES `un_flow_completion_execution` (`system_id`, `tenant_id`, `execution_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_compensation_version` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_compensation_identity` CHECK (((`compensation_id` > 0) and (`instance_id` > 0) and (`original_execution_id` > 0) and (`original_ordinal` between 0 and 7) and (`reverse_ordinal` between 0 and 7) and (`definition_id` > 0) and (`definition_version` > 0))),
  CONSTRAINT `ck_flow_compensation_lease` CHECK ((((`status` = _utf8mb4'LEASED') and (`lease_owner` is not null) and (char_length(trim(`lease_owner`)) between 1 and 160) and regexp_like(`lease_token_hash`,_utf8mb4'^[0-9a-f]{64}$') and (`lease_expires_at` is not null) and (`started_at` is not null) and (`attempt_count` > 0)) or ((`status` <> _utf8mb4'LEASED') and (`lease_owner` is null) and (`lease_token_hash` is null) and (`lease_expires_at` is null)))),
  CONSTRAINT `ck_flow_compensation_result` CHECK ((((`status` = _utf8mb4'SUCCEEDED') and (`result_json` is not null) and (json_type(`result_json`) = _utf8mb4'OBJECT') and (length(`result_json`) <= 8192) and (`failure_code` is null) and (`failure_message` is null) and (`failure_retryable` is null)) or ((`status` = _utf8mb4'FAILED') and (`result_json` is null) and regexp_like(`failure_code`,_utf8mb4'^[A-Z][A-Z0-9_]{0,63}$') and (char_length(trim(`failure_message`)) between 1 and 500) and (`failure_retryable` is not null)) or ((`status` not in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')) and (`result_json` is null) and (((`failure_code` is null) and (`failure_message` is null) and (`failure_retryable` is null)) or ((`status` in (_utf8mb4'RETRYING',_utf8mb4'CANCELLED')) and regexp_like(`failure_code`,_utf8mb4'^[A-Z][A-Z0-9_]{0,63}$') and (char_length(trim(`failure_message`)) between 1 and 500) and (`failure_retryable` is not null)))))),
  CONSTRAINT `ck_flow_compensation_status` CHECK ((`status` in (_utf8mb4'WAITING',_utf8mb4'AVAILABLE',_utf8mb4'LEASED',_utf8mb4'RETRYING',_utf8mb4'RUNNING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_flow_compensation_step` CHECK ((regexp_like(`step_code`,_utf8mb4'^[a-z][a-z0-9_]{0,63}$') and (char_length(trim(`step_name`)) between 1 and 80) and (`execution_type` in (_utf8mb4'EXTERNAL_TASK',_utf8mb4'WEBHOOK',_utf8mb4'SUBFLOW')) and (json_type(`config_json`) = _utf8mb4'OBJECT') and (length(`config_json`) <= 4096) and (json_type(`payload_json`) = _utf8mb4'OBJECT') and (length(`payload_json`) <= 65535))),
  CONSTRAINT `ck_flow_compensation_times` CHECK ((((`started_at` is null) or (`started_at` >= `created_at`)) and ((`terminal_at` is null) or (`terminal_at` >= `created_at`)) and (((`status` = _utf8mb4'WAITING') and (`available_at` is null) and (`started_at` is null) and (`terminal_at` is null)) or ((`status` in (_utf8mb4'AVAILABLE',_utf8mb4'RETRYING',_utf8mb4'LEASED')) and (`available_at` is not null) and (`terminal_at` is null)) or ((`status` = _utf8mb4'RUNNING') and (`execution_type` = _utf8mb4'SUBFLOW') and (`available_at` is not null) and (`started_at` is not null) and (`terminal_at` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'CANCELLED')) and (`terminal_at` is not null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_completion_compensation`
--

LOCK TABLES `un_flow_completion_compensation` WRITE;
/*!40000 ALTER TABLE `un_flow_completion_compensation` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_completion_compensation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_completion_execution`
--

DROP TABLE IF EXISTS `un_flow_completion_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_completion_execution` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `execution_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `ordinal` tinyint unsigned NOT NULL,
  `step_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `step_name` varchar(80) NOT NULL,
  `execution_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `parallel_group` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `config_json` json NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT '0',
  `state_version` int unsigned NOT NULL DEFAULT '0',
  `available_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(160) DEFAULT NULL,
  `lease_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `terminal_at` datetime(6) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `failure_message` varchar(500) DEFAULT NULL,
  `failure_retryable` tinyint(1) DEFAULT NULL,
  `active_slot` tinyint unsigned GENERATED ALWAYS AS ((case when (`status` in (_ascii'AVAILABLE',_ascii'LEASED',_ascii'RETRYING',_ascii'RUNNING',_ascii'FAILED')) then 1 else NULL end)) STORED,
  PRIMARY KEY (`system_id`,`tenant_id`,`execution_id`),
  UNIQUE KEY `uk_flow_completion_instance_ordinal` (`system_id`,`tenant_id`,`instance_id`,`ordinal`),
  KEY `idx_flow_completion_due` (`system_id`,`tenant_id`,`execution_type`,`status`,`available_at`,`execution_id`),
  KEY `idx_flow_completion_lease_due` (`system_id`,`tenant_id`,`status`,`lease_expires_at`,`execution_type`,`execution_id`),
  KEY `fk_flow_completion_version` (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  KEY `idx_flow_completion_active_stage` (`system_id`,`tenant_id`,`instance_id`,`parallel_group`,`status`,`ordinal`,`execution_id`),
  KEY `idx_flow_completion_stage_join` (`system_id`,`tenant_id`,`instance_id`,`ordinal`,`status`,`parallel_group`,`execution_id`),
  CONSTRAINT `fk_flow_completion_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_completion_version` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_completion_identity` CHECK (((`execution_id` > 0) and (`instance_id` > 0) and (`definition_id` > 0) and (`definition_version` > 0) and (`ordinal` between 0 and 7))),
  CONSTRAINT `ck_flow_completion_lease` CHECK ((((`status` = _ascii'LEASED') and (`lease_owner` is not null) and (char_length(trim(`lease_owner`)) between 1 and 160) and regexp_like(`lease_token_hash`,_ascii'^[0-9a-f]{64}$') and (`lease_expires_at` is not null) and (`started_at` is not null) and (`attempt_count` > 0)) or ((`status` <> _ascii'LEASED') and (`lease_owner` is null) and (`lease_token_hash` is null) and (`lease_expires_at` is null)))),
  CONSTRAINT `ck_flow_completion_parallel_group` CHECK (((`parallel_group` is null) or regexp_like(`parallel_group`,_utf8mb4'^[a-z][a-z0-9_]{0,63}$'))),
  CONSTRAINT `ck_flow_completion_result_failure` CHECK ((((`status` = _ascii'SUCCEEDED') and (`result_json` is not null) and (json_type(`result_json`) = _utf8mb4'OBJECT') and (length(`result_json`) <= 8192) and (`failure_code` is null) and (`failure_message` is null) and (`failure_retryable` is null)) or ((`status` = _ascii'FAILED') and (`result_json` is null) and regexp_like(`failure_code`,_ascii'^[A-Z][A-Z0-9_]{0,63}$') and (`failure_message` is not null) and (char_length(trim(`failure_message`)) between 1 and 500) and (`failure_retryable` is not null)) or ((`status` not in (_ascii'SUCCEEDED',_ascii'FAILED')) and (`result_json` is null) and (((`failure_code` is null) and (`failure_message` is null) and (`failure_retryable` is null)) or ((`status` in (_ascii'RETRYING',_ascii'CANCELLED')) and regexp_like(`failure_code`,_ascii'^[A-Z][A-Z0-9_]{0,63}$') and (`failure_message` is not null) and (char_length(trim(`failure_message`)) between 1 and 500) and (`failure_retryable` is not null)))))),
  CONSTRAINT `ck_flow_completion_status` CHECK ((`status` in (_ascii'WAITING',_ascii'AVAILABLE',_ascii'LEASED',_ascii'RETRYING',_ascii'RUNNING',_ascii'SUCCEEDED',_ascii'FAILED',_ascii'CANCELLED'))),
  CONSTRAINT `ck_flow_completion_step` CHECK ((regexp_like(`step_code`,_ascii'^[a-z][a-z0-9_]{0,63}$') and (char_length(trim(`step_name`)) between 1 and 80) and (`execution_type` in (_ascii'EXTERNAL_TASK',_ascii'WEBHOOK',_ascii'SUBFLOW')) and (json_type(`config_json`) = _utf8mb4'OBJECT') and (length(`config_json`) <= 4096) and (json_type(`payload_json`) = _utf8mb4'OBJECT') and (length(`payload_json`) <= 65535) and ((`execution_type` <> _ascii'SUBFLOW') or ((json_extract(`config_json`,_utf8mb4'$.definitionId') is not null) and (json_extract(`config_json`,_utf8mb4'$.version') is not null) and (json_type(json_extract(`config_json`,_utf8mb4'$.definitionId')) in (_utf8mb4'INTEGER',_utf8mb4'UNSIGNED INTEGER')) and (json_type(json_extract(`config_json`,_utf8mb4'$.version')) = _utf8mb4'INTEGER') and (cast(json_unquote(json_extract(`config_json`,_utf8mb4'$.definitionId')) as unsigned) > 0) and (cast(json_unquote(json_extract(`config_json`,_utf8mb4'$.version')) as unsigned) > 0))))),
  CONSTRAINT `ck_flow_completion_times` CHECK ((((`started_at` is null) or (`started_at` >= `created_at`)) and ((`terminal_at` is null) or (`terminal_at` >= `created_at`)) and (((`status` = _ascii'WAITING') and (`available_at` is null) and (`started_at` is null) and (`terminal_at` is null)) or ((`status` in (_ascii'AVAILABLE',_ascii'RETRYING')) and (`available_at` is not null) and (`terminal_at` is null)) or ((`status` = _ascii'LEASED') and (`available_at` is not null) and (`terminal_at` is null)) or ((`status` = _ascii'RUNNING') and (`execution_type` = _ascii'SUBFLOW') and (`available_at` is not null) and (`started_at` is not null) and (`terminal_at` is null)) or ((`status` in (_ascii'SUCCEEDED',_ascii'FAILED',_ascii'CANCELLED')) and (`terminal_at` is not null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_completion_execution`
--

LOCK TABLES `un_flow_completion_execution` WRITE;
/*!40000 ALTER TABLE `un_flow_completion_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_completion_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_copy_recipient`
--

DROP TABLE IF EXISTS `un_flow_copy_recipient`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_copy_recipient` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `copy_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `actor_id` bigint NOT NULL,
  `recipient_id` bigint NOT NULL,
  `message` varchar(500) NOT NULL DEFAULT '',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`copy_id`),
  UNIQUE KEY `uk_flow_copy_instance_recipient` (`system_id`,`tenant_id`,`instance_id`,`recipient_id`),
  KEY `idx_flow_copy_page` (`system_id`,`tenant_id`,`instance_id`,`created_at`,`copy_id`),
  CONSTRAINT `fk_flow_copy_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_copy_identity` CHECK (((`copy_id` > 0) and (`actor_id` > 0) and (`recipient_id` > 0) and (`actor_id` <> `recipient_id`))),
  CONSTRAINT `ck_flow_copy_message` CHECK ((char_length(`message`) <= 500))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_copy_recipient`
--

LOCK TABLES `un_flow_copy_recipient` WRITE;
/*!40000 ALTER TABLE `un_flow_copy_recipient` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_copy_recipient` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_decision_comment_template`
--

DROP TABLE IF EXISTS `un_flow_decision_comment_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_decision_comment_template` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `name` varchar(80) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `current_version` int unsigned NOT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`template_id`),
  UNIQUE KEY `uk_flow_comment_template_name` (`system_id`,`tenant_id`,`name`),
  KEY `idx_flow_comment_template_status` (`system_id`,`tenant_id`,`status`,`updated_at`,`template_id`),
  CONSTRAINT `ck_flow_comment_template_identity` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`template_id` > 0) and (`created_by` > 0) and (`updated_by` > 0) and (`current_version` > 0))),
  CONSTRAINT `ck_flow_comment_template_name` CHECK ((char_length(trim(`name`)) between 1 and 80)),
  CONSTRAINT `ck_flow_comment_template_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE'))),
  CONSTRAINT `ck_flow_comment_template_time` CHECK ((`updated_at` >= `created_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_decision_comment_template`
--

LOCK TABLES `un_flow_decision_comment_template` WRITE;
/*!40000 ALTER TABLE `un_flow_decision_comment_template` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_decision_comment_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_decision_comment_template_version`
--

DROP TABLE IF EXISTS `un_flow_decision_comment_template_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_decision_comment_template_version` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `name` varchar(80) NOT NULL,
  `comment_body` varchar(1000) NOT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`template_id`,`version_no`),
  CONSTRAINT `fk_flow_comment_template_version` FOREIGN KEY (`system_id`, `tenant_id`, `template_id`) REFERENCES `un_flow_decision_comment_template` (`system_id`, `tenant_id`, `template_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_comment_template_version_body` CHECK ((char_length(trim(`comment_body`)) between 1 and 1000)),
  CONSTRAINT `ck_flow_comment_template_version_identity` CHECK (((`template_id` > 0) and (`version_no` > 0) and (`created_by` > 0))),
  CONSTRAINT `ck_flow_comment_template_version_name` CHECK ((char_length(trim(`name`)) between 1 and 80))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_decision_comment_template_version`
--

LOCK TABLES `un_flow_decision_comment_template_version` WRITE;
/*!40000 ALTER TABLE `un_flow_decision_comment_template_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_decision_comment_template_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_decision_evidence`
--

DROP TABLE IF EXISTS `un_flow_decision_evidence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_decision_evidence` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `evidence_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `history_sequence` int unsigned NOT NULL,
  `branch_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stage_index` tinyint unsigned NOT NULL,
  `decision` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `signature_kind` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `signature_file_id` bigint DEFAULT NULL,
  `typed_signature` varchar(120) DEFAULT NULL,
  `template_id` bigint DEFAULT NULL,
  `template_version` int unsigned DEFAULT NULL,
  `template_name` varchar(80) DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `represented_member_id` bigint NOT NULL,
  `delegation_id` bigint DEFAULT NULL,
  `decided_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`evidence_id`),
  UNIQUE KEY `uk_flow_decision_evidence_history` (`system_id`,`tenant_id`,`instance_id`,`history_sequence`),
  KEY `idx_flow_decision_evidence_instance` (`system_id`,`tenant_id`,`instance_id`,`decided_at`,`evidence_id`),
  KEY `idx_flow_decision_evidence_template` (`system_id`,`tenant_id`,`template_id`,`template_version`),
  KEY `fk_flow_decision_evidence_delegation` (`system_id`,`tenant_id`,`delegation_id`),
  CONSTRAINT `fk_flow_decision_evidence_delegation` FOREIGN KEY (`system_id`, `tenant_id`, `delegation_id`) REFERENCES `un_flow_approval_delegation` (`system_id`, `tenant_id`, `delegation_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_decision_evidence_history` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`, `history_sequence`) REFERENCES `un_flow_history_event` (`system_id`, `tenant_id`, `instance_id`, `event_sequence`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_decision_evidence_template` FOREIGN KEY (`system_id`, `tenant_id`, `template_id`, `template_version`) REFERENCES `un_flow_decision_comment_template_version` (`system_id`, `tenant_id`, `template_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_decision_evidence_actor` CHECK ((((`actor_id` = `represented_member_id`) and (`delegation_id` is null)) or ((`actor_id` <> `represented_member_id`) and (`delegation_id` is not null) and (`delegation_id` > 0)))),
  CONSTRAINT `ck_flow_decision_evidence_branch` CHECK (((`branch_code` is null) or regexp_like(`branch_code`,_utf8mb4'^[a-z][a-z0-9_]{0,63}$'))),
  CONSTRAINT `ck_flow_decision_evidence_decision` CHECK ((`decision` in (_utf8mb4'APPROVED',_utf8mb4'REJECTED'))),
  CONSTRAINT `ck_flow_decision_evidence_identity` CHECK (((`evidence_id` > 0) and (`instance_id` > 0) and (`history_sequence` > 1) and (`stage_index` <= 9) and (`actor_id` > 0) and (`represented_member_id` > 0))),
  CONSTRAINT `ck_flow_decision_evidence_signature` CHECK ((((`signature_kind` is null) and (`signature_file_id` is null) and (`typed_signature` is null)) or ((`signature_kind` = _utf8mb4'FILE') and (`signature_file_id` is not null) and (`signature_file_id` > 0) and (`typed_signature` is null)) or ((`signature_kind` = _utf8mb4'TYPED') and (`signature_file_id` is null) and (`typed_signature` is not null) and (char_length(trim(`typed_signature`)) between 1 and 120)))),
  CONSTRAINT `ck_flow_decision_evidence_template` CHECK ((((`template_id` is null) and (`template_version` is null) and (`template_name` is null)) or ((`template_id` is not null) and (`template_id` > 0) and (`template_version` is not null) and (`template_version` > 0) and (`template_name` is not null) and (char_length(trim(`template_name`)) between 1 and 80))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_decision_evidence`
--

LOCK TABLES `un_flow_decision_evidence` WRITE;
/*!40000 ALTER TABLE `un_flow_decision_evidence` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_decision_evidence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_decision_evidence_file`
--

DROP TABLE IF EXISTS `un_flow_decision_evidence_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_decision_evidence_file` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `evidence_id` bigint NOT NULL,
  `file_id` bigint NOT NULL,
  `original_name` varchar(255) NOT NULL,
  `content_type` varchar(255) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `size_bytes` bigint unsigned NOT NULL,
  `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `is_attachment` tinyint(1) NOT NULL,
  `is_signature` tinyint(1) NOT NULL,
  `attachment_order` tinyint unsigned DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`evidence_id`,`file_id`),
  UNIQUE KEY `uk_flow_decision_evidence_attachment_order` (`system_id`,`tenant_id`,`evidence_id`,`attachment_order`),
  CONSTRAINT `fk_flow_decision_evidence_file` FOREIGN KEY (`system_id`, `tenant_id`, `evidence_id`) REFERENCES `un_flow_decision_evidence` (`system_id`, `tenant_id`, `evidence_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_decision_evidence_file_identity` CHECK (((`file_id` > 0) and (char_length(trim(`original_name`)) between 1 and 255) and (char_length(trim(`content_type`)) between 1 and 255) and regexp_like(`sha256`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_flow_decision_evidence_file_roles` CHECK ((((`is_attachment` = true) or (`is_signature` = true)) and (((`is_attachment` = true) and (`attachment_order` between 0 and 4)) or ((`is_attachment` = false) and (`attachment_order` is null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_decision_evidence_file`
--

LOCK TABLES `un_flow_decision_evidence_file` WRITE;
/*!40000 ALTER TABLE `un_flow_decision_evidence_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_decision_evidence_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_definition_draft`
--

DROP TABLE IF EXISTS `un_flow_definition_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_definition_draft` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `approver_id` bigint NOT NULL,
  `approval_mode` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SEQUENTIAL',
  `trigger_module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `trigger_event` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `trigger_priority` int DEFAULT NULL,
  `trigger_exclusive` tinyint(1) DEFAULT NULL,
  `trigger_conditions` json DEFAULT NULL,
  `trigger_start_at` datetime(6) DEFAULT NULL,
  `trigger_interval_minutes` int unsigned DEFAULT NULL,
  `trigger_requester_id` bigint DEFAULT NULL,
  `gateway_branches` json DEFAULT NULL,
  `parallel_branches` json DEFAULT NULL,
  `inclusive_branches` json DEFAULT NULL,
  `approver_sources` json DEFAULT NULL,
  `quorum_rules` json DEFAULT NULL,
  `deadline_policies` json DEFAULT NULL,
  `decision_comment_policies` json DEFAULT NULL,
  `decision_evidence_policies` json DEFAULT NULL,
  `completion_failure_policy` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL_RETRY',
  `completion_steps` json DEFAULT NULL,
  `approval_stages` json DEFAULT NULL,
  `status_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_approved_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_rejected_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_withdrawn_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_terminated_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `revision` int unsigned NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`definition_id`),
  KEY `idx_flow_draft_approver` (`system_id`,`tenant_id`,`approver_id`,`updated_at`),
  KEY `idx_flow_draft_trigger` (`system_id`,`tenant_id`,`trigger_module_code`,`trigger_event`,`trigger_priority` DESC,`definition_id`),
  CONSTRAINT `ck_flow_draft_approval_mode` CHECK ((`approval_mode` in (_ascii'SEQUENTIAL',_ascii'ANY',_ascii'ALL',_ascii'QUORUM'))),
  CONSTRAINT `ck_flow_draft_approval_stages` CHECK (((`approval_stages` is null) or ((json_type(`approval_stages`) = _utf8mb4'ARRAY') and (json_length(`approval_stages`) between 2 and 10) and (length(`approval_stages`) <= 262144)))),
  CONSTRAINT `ck_flow_draft_approver` CHECK ((`approver_id` > 0)),
  CONSTRAINT `ck_flow_draft_completion_failure_policy` CHECK ((`completion_failure_policy` in (_utf8mb4'MANUAL_RETRY',_utf8mb4'COMPENSATE'))),
  CONSTRAINT `ck_flow_draft_completion_steps` CHECK (((`completion_steps` is null) or ((json_type(`completion_steps`) = _utf8mb4'ARRAY') and (json_length(`completion_steps`) between 1 and 8) and (length(`completion_steps`) <= 16384)))),
  CONSTRAINT `ck_flow_draft_deadline_policies` CHECK (((`deadline_policies` is null) or (json_type(`deadline_policies`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_draft_decision_comment_policies` CHECK (((`decision_comment_policies` is null) or (json_type(`decision_comment_policies`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_draft_decision_evidence` CHECK (((`decision_evidence_policies` is null) or ((json_type(`decision_evidence_policies`) = _utf8mb4'OBJECT') and (length(`decision_evidence_policies`) <= 16384)))),
  CONSTRAINT `ck_flow_draft_gateway` CHECK (((`gateway_branches` is null) or ((json_type(`gateway_branches`) = _utf8mb4'ARRAY') and (json_length(`gateway_branches`) between 2 and 6)))),
  CONSTRAINT `ck_flow_draft_inclusive_gateway` CHECK ((((`inclusive_branches` is null) or ((`gateway_branches` is null) and (`parallel_branches` is null))) and ((`inclusive_branches` is null) or ((json_type(`inclusive_branches`) = _utf8mb4'ARRAY') and (json_length(`inclusive_branches`) between 2 and 5))))),
  CONSTRAINT `ck_flow_draft_name` CHECK ((char_length(trim(`name`)) between 1 and 160)),
  CONSTRAINT `ck_flow_draft_parallel_gateway` CHECK ((((`gateway_branches` is null) or (`parallel_branches` is null)) and ((`parallel_branches` is null) or ((json_type(`parallel_branches`) = _utf8mb4'ARRAY') and (json_length(`parallel_branches`) between 2 and 5))))),
  CONSTRAINT `ck_flow_draft_quorum_rules` CHECK (((`quorum_rules` is null) or (json_type(`quorum_rules`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_draft_record_status_mapping` CHECK ((((`status_field_code` is null) and (`status_approved_value` is null) and (`status_rejected_value` is null) and (`status_withdrawn_value` is null) and (`status_terminated_value` is null)) or ((`status_field_code` is not null) and regexp_like(`status_field_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`status_approved_value` is not null) and regexp_like(`status_approved_value`,_ascii'^[1-9][0-9]{0,18}$') and (`status_rejected_value` is not null) and regexp_like(`status_rejected_value`,_ascii'^[1-9][0-9]{0,18}$') and (`status_withdrawn_value` is not null) and regexp_like(`status_withdrawn_value`,_ascii'^[1-9][0-9]{0,18}$') and (`status_terminated_value` is not null) and regexp_like(`status_terminated_value`,_ascii'^[1-9][0-9]{0,18}$')))),
  CONSTRAINT `ck_flow_draft_revision` CHECK ((`revision` > 0)),
  CONSTRAINT `ck_flow_draft_scope` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`definition_id` > 0))),
  CONSTRAINT `ck_flow_draft_time` CHECK ((`updated_at` >= `created_at`)),
  CONSTRAINT `ck_flow_draft_trigger_binding` CHECK ((((`trigger_module_code` is null) and (`trigger_event` is null) and (`trigger_priority` is null) and (`trigger_exclusive` is null) and (`trigger_conditions` is null) and (`trigger_start_at` is null) and (`trigger_interval_minutes` is null) and (`trigger_requester_id` is null)) or ((`trigger_module_code` is not null) and regexp_like(`trigger_module_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`trigger_event` is not null) and (`trigger_event` in (_ascii'RECORD_ACTIVATED',_ascii'RECORD_CREATED',_ascii'RECORD_UPDATED',_ascii'RECORD_DELETED',_ascii'RECORD_STATUS_CHANGED',_ascii'IMPORT_COMPLETED')) and (`trigger_priority` is not null) and (`trigger_priority` between -(1000) and 1000) and (`trigger_exclusive` is not null) and (`trigger_exclusive` in (false,true)) and (`trigger_conditions` is not null) and (json_type(`trigger_conditions`) = _utf8mb4'ARRAY') and (json_length(`trigger_conditions`) <= 10) and (`trigger_start_at` is null) and (`trigger_interval_minutes` is null) and (`trigger_requester_id` is null)) or ((`trigger_module_code` is null) and (`trigger_event` is not null) and (`trigger_event` = _ascii'PERIODIC') and (`trigger_priority` is not null) and (`trigger_priority` = 0) and (`trigger_exclusive` is not null) and (`trigger_exclusive` = true) and (`trigger_conditions` is not null) and (json_type(`trigger_conditions`) = _utf8mb4'ARRAY') and (json_length(`trigger_conditions`) = 0) and (`trigger_start_at` is not null) and (`trigger_interval_minutes` is not null) and (`trigger_interval_minutes` between 1 and 525600) and (`trigger_requester_id` is not null) and (`trigger_requester_id` > 0)))),
  CONSTRAINT `ck_flow_draft_trigger_status_mapping` CHECK (((`status_field_code` is null) or (`trigger_event` is null) or (`trigger_event` = _ascii'RECORD_ACTIVATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_definition_draft`
--

LOCK TABLES `un_flow_definition_draft` WRITE;
/*!40000 ALTER TABLE `un_flow_definition_draft` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_definition_draft` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_definition_draft_step`
--

DROP TABLE IF EXISTS `un_flow_definition_draft_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_definition_draft_step` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `step_no` int unsigned NOT NULL,
  `approver_id` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`definition_id`,`step_no`),
  KEY `idx_flow_draft_step_approver` (`system_id`,`tenant_id`,`approver_id`,`definition_id`,`step_no`),
  CONSTRAINT `fk_flow_draft_step_draft` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`) REFERENCES `un_flow_definition_draft` (`system_id`, `tenant_id`, `definition_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_draft_step_approver` CHECK ((`approver_id` > 0)),
  CONSTRAINT `ck_flow_draft_step_number` CHECK ((`step_no` between 1 and 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_definition_draft_step`
--

LOCK TABLES `un_flow_definition_draft_step` WRITE;
/*!40000 ALTER TABLE `un_flow_definition_draft_step` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_definition_draft_step` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_definition_extension_draft`
--

DROP TABLE IF EXISTS `un_flow_definition_extension_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_definition_extension_draft` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `source_revision` int unsigned NOT NULL,
  `graph_json` json NOT NULL,
  `graph_checksum` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`definition_id`),
  CONSTRAINT `fk_flow_extension_draft_definition` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`) REFERENCES `un_flow_definition_draft` (`system_id`, `tenant_id`, `definition_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_extension_draft_checksum` CHECK (regexp_like(`graph_checksum`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_flow_extension_draft_graph` CHECK (((json_type(`graph_json`) = _utf8mb4'OBJECT') and (json_length(json_extract(`graph_json`,_utf8mb4'$.nodes')) between 2 and 200))),
  CONSTRAINT `ck_flow_extension_draft_identity` CHECK (((`source_revision` > 0) and (`updated_by` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_definition_extension_draft`
--

LOCK TABLES `un_flow_definition_extension_draft` WRITE;
/*!40000 ALTER TABLE `un_flow_definition_extension_draft` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_definition_extension_draft` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_definition_extension_version`
--

DROP TABLE IF EXISTS `un_flow_definition_extension_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_definition_extension_version` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `source_revision` int unsigned NOT NULL,
  `graph_json` json NOT NULL,
  `graph_checksum` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `published_by` bigint NOT NULL,
  `published_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  UNIQUE KEY `uk_flow_extension_version_revision` (`system_id`,`tenant_id`,`definition_id`,`source_revision`),
  KEY `idx_flow_extension_version_checksum` (`system_id`,`graph_checksum`,`published_at`),
  CONSTRAINT `fk_flow_extension_version_definition` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_extension_version_checksum` CHECK (regexp_like(`graph_checksum`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_flow_extension_version_graph` CHECK (((json_type(`graph_json`) = _utf8mb4'OBJECT') and (json_length(json_extract(`graph_json`,_utf8mb4'$.nodes')) between 2 and 200))),
  CONSTRAINT `ck_flow_extension_version_identity` CHECK (((`definition_version` > 0) and (`source_revision` > 0) and (`published_by` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_definition_extension_version`
--

LOCK TABLES `un_flow_definition_extension_version` WRITE;
/*!40000 ALTER TABLE `un_flow_definition_extension_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_definition_extension_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_definition_version`
--

DROP TABLE IF EXISTS `un_flow_definition_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_definition_version` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `name` varchar(160) NOT NULL,
  `approver_id` bigint NOT NULL,
  `approval_mode` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SEQUENTIAL',
  `trigger_module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `trigger_event` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `trigger_priority` int DEFAULT NULL,
  `trigger_exclusive` tinyint(1) DEFAULT NULL,
  `trigger_conditions` json DEFAULT NULL,
  `trigger_start_at` datetime(6) DEFAULT NULL,
  `trigger_interval_minutes` int unsigned DEFAULT NULL,
  `trigger_requester_id` bigint DEFAULT NULL,
  `gateway_branches` json DEFAULT NULL,
  `parallel_branches` json DEFAULT NULL,
  `inclusive_branches` json DEFAULT NULL,
  `approver_sources` json DEFAULT NULL,
  `quorum_rules` json DEFAULT NULL,
  `deadline_policies` json DEFAULT NULL,
  `decision_comment_policies` json DEFAULT NULL,
  `decision_evidence_policies` json DEFAULT NULL,
  `completion_failure_policy` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL_RETRY',
  `completion_steps` json DEFAULT NULL,
  `approval_stages` json DEFAULT NULL,
  `status_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_approved_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_rejected_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_withdrawn_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_terminated_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `source_revision` int unsigned NOT NULL,
  `published_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`definition_id`,`version_no`),
  UNIQUE KEY `uk_flow_version_source_revision` (`system_id`,`tenant_id`,`definition_id`,`source_revision`),
  KEY `idx_flow_version_approver` (`system_id`,`tenant_id`,`approver_id`,`published_at`,`definition_id`),
  KEY `idx_flow_version_trigger` (`system_id`,`tenant_id`,`trigger_module_code`,`trigger_event`,`trigger_priority` DESC,`definition_id`,`version_no`),
  CONSTRAINT `fk_flow_version_draft` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`) REFERENCES `un_flow_definition_draft` (`system_id`, `tenant_id`, `definition_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_version_approval_mode` CHECK ((`approval_mode` in (_ascii'SEQUENTIAL',_ascii'ANY',_ascii'ALL',_ascii'QUORUM'))),
  CONSTRAINT `ck_flow_version_approval_stages` CHECK (((`approval_stages` is null) or ((json_type(`approval_stages`) = _utf8mb4'ARRAY') and (json_length(`approval_stages`) between 2 and 10) and (length(`approval_stages`) <= 262144)))),
  CONSTRAINT `ck_flow_version_approver` CHECK ((`approver_id` > 0)),
  CONSTRAINT `ck_flow_version_completion_failure_policy` CHECK ((`completion_failure_policy` in (_utf8mb4'MANUAL_RETRY',_utf8mb4'COMPENSATE'))),
  CONSTRAINT `ck_flow_version_completion_steps` CHECK (((`completion_steps` is null) or ((json_type(`completion_steps`) = _utf8mb4'ARRAY') and (json_length(`completion_steps`) between 1 and 8) and (length(`completion_steps`) <= 16384)))),
  CONSTRAINT `ck_flow_version_deadline_policies` CHECK (((`deadline_policies` is null) or (json_type(`deadline_policies`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_version_decision_comment_policies` CHECK (((`decision_comment_policies` is null) or (json_type(`decision_comment_policies`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_version_decision_evidence` CHECK (((`decision_evidence_policies` is null) or ((json_type(`decision_evidence_policies`) = _utf8mb4'OBJECT') and (length(`decision_evidence_policies`) <= 16384)))),
  CONSTRAINT `ck_flow_version_gateway` CHECK (((`gateway_branches` is null) or ((json_type(`gateway_branches`) = _utf8mb4'ARRAY') and (json_length(`gateway_branches`) between 2 and 6)))),
  CONSTRAINT `ck_flow_version_inclusive_gateway` CHECK ((((`inclusive_branches` is null) or ((`gateway_branches` is null) and (`parallel_branches` is null))) and ((`inclusive_branches` is null) or ((json_type(`inclusive_branches`) = _utf8mb4'ARRAY') and (json_length(`inclusive_branches`) between 2 and 5))))),
  CONSTRAINT `ck_flow_version_name` CHECK ((char_length(trim(`name`)) between 1 and 160)),
  CONSTRAINT `ck_flow_version_number` CHECK (((`version_no` > 0) and (`source_revision` > 0))),
  CONSTRAINT `ck_flow_version_parallel_gateway` CHECK ((((`gateway_branches` is null) or (`parallel_branches` is null)) and ((`parallel_branches` is null) or ((json_type(`parallel_branches`) = _utf8mb4'ARRAY') and (json_length(`parallel_branches`) between 2 and 5))))),
  CONSTRAINT `ck_flow_version_quorum_rules` CHECK (((`quorum_rules` is null) or (json_type(`quorum_rules`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_version_record_status_mapping` CHECK ((((`status_field_code` is null) and (`status_approved_value` is null) and (`status_rejected_value` is null) and (`status_withdrawn_value` is null) and (`status_terminated_value` is null)) or ((`status_field_code` is not null) and regexp_like(`status_field_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`status_approved_value` is not null) and regexp_like(`status_approved_value`,_ascii'^[1-9][0-9]{0,18}$') and (`status_rejected_value` is not null) and regexp_like(`status_rejected_value`,_ascii'^[1-9][0-9]{0,18}$') and (`status_withdrawn_value` is not null) and regexp_like(`status_withdrawn_value`,_ascii'^[1-9][0-9]{0,18}$') and (`status_terminated_value` is not null) and regexp_like(`status_terminated_value`,_ascii'^[1-9][0-9]{0,18}$')))),
  CONSTRAINT `ck_flow_version_trigger_binding` CHECK ((((`trigger_module_code` is null) and (`trigger_event` is null) and (`trigger_priority` is null) and (`trigger_exclusive` is null) and (`trigger_conditions` is null) and (`trigger_start_at` is null) and (`trigger_interval_minutes` is null) and (`trigger_requester_id` is null)) or ((`trigger_module_code` is not null) and regexp_like(`trigger_module_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`trigger_event` is not null) and (`trigger_event` in (_ascii'RECORD_ACTIVATED',_ascii'RECORD_CREATED',_ascii'RECORD_UPDATED',_ascii'RECORD_DELETED',_ascii'RECORD_STATUS_CHANGED',_ascii'IMPORT_COMPLETED')) and (`trigger_priority` is not null) and (`trigger_priority` between -(1000) and 1000) and (`trigger_exclusive` is not null) and (`trigger_exclusive` in (false,true)) and (`trigger_conditions` is not null) and (json_type(`trigger_conditions`) = _utf8mb4'ARRAY') and (json_length(`trigger_conditions`) <= 10) and (`trigger_start_at` is null) and (`trigger_interval_minutes` is null) and (`trigger_requester_id` is null)) or ((`trigger_module_code` is null) and (`trigger_event` is not null) and (`trigger_event` = _ascii'PERIODIC') and (`trigger_priority` is not null) and (`trigger_priority` = 0) and (`trigger_exclusive` is not null) and (`trigger_exclusive` = true) and (`trigger_conditions` is not null) and (json_type(`trigger_conditions`) = _utf8mb4'ARRAY') and (json_length(`trigger_conditions`) = 0) and (`trigger_start_at` is not null) and (`trigger_interval_minutes` is not null) and (`trigger_interval_minutes` between 1 and 525600) and (`trigger_requester_id` is not null) and (`trigger_requester_id` > 0)))),
  CONSTRAINT `ck_flow_version_trigger_status_mapping` CHECK (((`status_field_code` is null) or (`trigger_event` is null) or (`trigger_event` = _ascii'RECORD_ACTIVATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_definition_version`
--

LOCK TABLES `un_flow_definition_version` WRITE;
/*!40000 ALTER TABLE `un_flow_definition_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_definition_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_definition_version_step`
--

DROP TABLE IF EXISTS `un_flow_definition_version_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_definition_version_step` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `step_no` int unsigned NOT NULL,
  `approver_id` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`definition_id`,`version_no`,`step_no`),
  KEY `idx_flow_version_step_approver` (`system_id`,`tenant_id`,`approver_id`,`definition_id`,`version_no`,`step_no`),
  CONSTRAINT `fk_flow_version_step_version` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `version_no`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_version_step_approver` CHECK ((`approver_id` > 0)),
  CONSTRAINT `ck_flow_version_step_number` CHECK ((`step_no` between 1 and 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_definition_version_step`
--

LOCK TABLES `un_flow_definition_version_step` WRITE;
/*!40000 ALTER TABLE `un_flow_definition_version_step` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_definition_version_step` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_form_write_history`
--

DROP TABLE IF EXISTS `un_flow_form_write_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_form_write_history` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `node_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `history_sequence` int unsigned NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `record_version_before` bigint unsigned NOT NULL,
  `record_version_after` bigint unsigned NOT NULL,
  `changes_json` json NOT NULL,
  `before_json` json NOT NULL,
  `after_json` json NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`instance_id`,`node_code`,`history_sequence`),
  KEY `idx_flow_form_history_actor` (`system_id`,`tenant_id`,`actor_member_id`,`occurred_at`,`instance_id`),
  CONSTRAINT `fk_flow_form_history_snapshot` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`, `node_code`) REFERENCES `un_flow_instance_form_snapshot` (`system_id`, `tenant_id`, `instance_id`, `node_code`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_form_history_identity` CHECK (((`history_sequence` > 0) and (`actor_member_id` > 0) and (`record_version_after` > `record_version_before`))),
  CONSTRAINT `ck_flow_form_history_json` CHECK (((json_type(`changes_json`) = _utf8mb4'OBJECT') and (json_type(`before_json`) = _utf8mb4'OBJECT') and (json_type(`after_json`) = _utf8mb4'OBJECT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_form_write_history`
--

LOCK TABLES `un_flow_form_write_history` WRITE;
/*!40000 ALTER TABLE `un_flow_form_write_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_form_write_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_history_event`
--

DROP TABLE IF EXISTS `un_flow_history_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_history_event` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `event_sequence` int unsigned NOT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_id` bigint NOT NULL,
  `represented_member_id` bigint DEFAULT NULL,
  `delegation_id` bigint DEFAULT NULL,
  `from_status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `comment` varchar(1000) NOT NULL DEFAULT '',
  `occurred_at` datetime(6) NOT NULL,
  `target_member_id` bigint DEFAULT NULL,
  `assignment_position` varchar(8) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `target_step_index` int unsigned DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`instance_id`,`event_sequence`),
  KEY `idx_flow_history_actor` (`system_id`,`tenant_id`,`actor_id`,`occurred_at`,`instance_id`),
  KEY `idx_flow_history_represented` (`system_id`,`tenant_id`,`represented_member_id`,`occurred_at`,`instance_id`),
  KEY `fk_flow_history_represented_member` (`system_id`,`represented_member_id`,`tenant_id`),
  KEY `fk_flow_history_delegation` (`system_id`,`tenant_id`,`delegation_id`),
  CONSTRAINT `fk_flow_history_delegation` FOREIGN KEY (`system_id`, `tenant_id`, `delegation_id`) REFERENCES `un_flow_approval_delegation` (`system_id`, `tenant_id`, `delegation_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_history_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_history_represented_member` FOREIGN KEY (`system_id`, `represented_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_history_actor` CHECK ((`actor_id` > 0)),
  CONSTRAINT `ck_flow_history_delegation_actor` CHECK ((((`represented_member_id` is null) and (`delegation_id` is null)) or ((`represented_member_id` is not null) and (`represented_member_id` > 0) and (`delegation_id` is not null) and (`delegation_id` > 0) and (`actor_id` <> `represented_member_id`)))),
  CONSTRAINT `ck_flow_history_event` CHECK ((((`event_sequence` = 1) and (`event_type` = _utf8mb4'STARTED') and (`from_status` is null) and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'APPROVED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED')) and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'REJECTED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` in (_utf8mb4'PENDING',_utf8mb4'REJECTED')) and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'WITHDRAWN') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'WITHDRAWN') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'TERMINATED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'TERMINATED') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'TRANSFERRED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` > 0) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'ADD_SIGNED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` > 0) and (`assignment_position` in (_utf8mb4'BEFORE',_utf8mb4'AFTER')) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'RETURNED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` > 0) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'CLAIM_CANCELLED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'CLAIMED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` > 0) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(`comment`) <= 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'SIGN_REMOVED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` > 0) and (`assignment_position` is null) and (`target_step_index` is not null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` in (_utf8mb4'DEADLINE_REMINDER_SENT',_utf8mb4'DEADLINE_OVERDUE')) and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'PENDING') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'DEADLINE_AUTO_APPROVED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED')) and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'DEADLINE_AUTO_REJECTED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'REJECTED') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (char_length(trim(`comment`)) between 1 and 500)) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'COMPLETION_COMPLETED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'APPROVED') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (`comment` = _utf8mb4'')) or ((`event_sequence` >= 2) and (`event_type` = _utf8mb4'COMPLETION_COMPENSATED') and (`from_status` = _utf8mb4'PENDING') and (`to_status` = _utf8mb4'TERMINATED') and (`target_member_id` is null) and (`assignment_position` is null) and (`target_step_index` is null) and (`comment` = _utf8mb4'')))),
  CONSTRAINT `ck_flow_history_sequence` CHECK ((`event_sequence` between 1 and 2147483647))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_history_event`
--

LOCK TABLES `un_flow_history_event` WRITE;
/*!40000 ALTER TABLE `un_flow_history_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_history_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_instance`
--

DROP TABLE IF EXISTS `un_flow_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_instance` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `business_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `record_id` bigint DEFAULT NULL,
  `requester_id` bigint NOT NULL,
  `approver_id` bigint NOT NULL,
  `approver_ids_json` json NOT NULL,
  `approval_mode` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SEQUENTIAL',
  `required_approvals` tinyint unsigned NOT NULL DEFAULT '1',
  `deadline_policy` json DEFAULT NULL,
  `deadline_remind_at` datetime(6) DEFAULT NULL,
  `deadline_due_at` datetime(6) DEFAULT NULL,
  `deadline_reminded_at` datetime(6) DEFAULT NULL,
  `deadline_processed_at` datetime(6) DEFAULT NULL,
  `decision_comment_policy` json DEFAULT NULL,
  `decision_evidence_policy` json DEFAULT NULL,
  `approval_stage_state` json DEFAULT NULL,
  `start_context` json DEFAULT NULL,
  `approved_approver_ids_json` json NOT NULL DEFAULT (json_array()),
  `rejected_approver_ids_json` json NOT NULL DEFAULT (json_array()),
  `current_step_index` int unsigned NOT NULL,
  `current_stage_index` int unsigned NOT NULL DEFAULT '0',
  `claim_state` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `completion_phase` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'HUMAN_APPROVAL',
  `completion_failure_policy` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL_RETRY',
  `active_completion_ordinal` tinyint unsigned DEFAULT NULL,
  `state_version` int unsigned NOT NULL DEFAULT '0',
  `started_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`instance_id`),
  UNIQUE KEY `uk_flow_instance_business` (`system_id`,`tenant_id`,`definition_id`,`business_key`),
  KEY `idx_flow_instance_inbox` (`system_id`,`tenant_id`,`approver_id`,`status`,`started_at`,`instance_id`),
  KEY `idx_flow_instance_requester` (`system_id`,`tenant_id`,`requester_id`,`started_at`,`instance_id`),
  KEY `fk_flow_instance_version` (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  KEY `idx_flow_instance_claim_pool` (`system_id`,`tenant_id`,`status`,`claim_state`,`started_at`,`instance_id`),
  KEY `idx_flow_instance_record` (`system_id`,`tenant_id`,`module_code`,`record_id`,`started_at`,`instance_id`),
  KEY `ix_flow_instance_deadline_poll` (`system_id`,`tenant_id`,`status`,`deadline_processed_at`,`deadline_remind_at`,`deadline_due_at`),
  CONSTRAINT `fk_flow_instance_version` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_instance_approval_decisions` CHECK (((json_type(`approved_approver_ids_json`) = _utf8mb4'ARRAY') and (json_type(`rejected_approver_ids_json`) = _utf8mb4'ARRAY') and (json_length(`approved_approver_ids_json`) between 0 and 10) and (json_length(`rejected_approver_ids_json`) between 0 and 10) and ((json_length(`approved_approver_ids_json`) + json_length(`rejected_approver_ids_json`)) <= 10) and ((`approval_mode` <> _ascii'SEQUENTIAL') or ((json_length(`approved_approver_ids_json`) = 0) and (json_length(`rejected_approver_ids_json`) = 0))))),
  CONSTRAINT `ck_flow_instance_approval_mode` CHECK ((`approval_mode` in (_ascii'SEQUENTIAL',_ascii'ANY',_ascii'ALL',_ascii'QUORUM'))),
  CONSTRAINT `ck_flow_instance_approval_stage_state` CHECK ((((`approval_stage_state` is null) and (`current_stage_index` = 0)) or ((json_type(`approval_stage_state`) = _utf8mb4'ARRAY') and (json_length(`approval_stage_state`) between 1 and 10) and (`current_stage_index` < json_length(`approval_stage_state`)) and (length(`approval_stage_state`) <= 262144)))),
  CONSTRAINT `ck_flow_instance_approver_snapshot` CHECK (((json_type(`approver_ids_json`) = _utf8mb4'ARRAY') and (json_length(`approver_ids_json`) between 1 and 10) and (`current_step_index` < json_length(`approver_ids_json`)) and (json_extract(`approver_ids_json`,concat(_utf8mb4'$[',`current_step_index`,_utf8mb4']')) is not null) and (cast(json_unquote(json_extract(`approver_ids_json`,concat(_utf8mb4'$[',`current_step_index`,_utf8mb4']'))) as unsigned) = `approver_id`))),
  CONSTRAINT `ck_flow_instance_business` CHECK ((char_length(trim(`business_key`)) between 1 and 160)),
  CONSTRAINT `ck_flow_instance_claim_state` CHECK ((`claim_state` in (_ascii'CLAIMED',_ascii'OPEN'))),
  CONSTRAINT `ck_flow_instance_compensating_policy` CHECK (((`completion_phase` <> _utf8mb4'COMPENSATING') or (`completion_failure_policy` = _utf8mb4'COMPENSATE'))),
  CONSTRAINT `ck_flow_instance_completion` CHECK ((((`status` = _ascii'PENDING') and (`completed_at` is null)) or ((`status` in (_ascii'APPROVED',_ascii'REJECTED',_ascii'WITHDRAWN',_ascii'TERMINATED')) and (`state_version` between 1 and 2147483647) and (`completed_at` is not null) and (`completed_at` >= `started_at`)))),
  CONSTRAINT `ck_flow_instance_completion_failure_policy` CHECK ((`completion_failure_policy` in (_utf8mb4'MANUAL_RETRY',_utf8mb4'COMPENSATE'))),
  CONSTRAINT `ck_flow_instance_completion_ordinal` CHECK ((((`completion_phase` = _ascii'EXTERNAL_EXECUTION') and (`active_completion_ordinal` between 0 and 7)) or ((`completion_phase` <> _ascii'EXTERNAL_EXECUTION') and (`active_completion_ordinal` is null)))),
  CONSTRAINT `ck_flow_instance_completion_phase` CHECK ((((`status` = _utf8mb4'PENDING') and (`completion_phase` in (_utf8mb4'HUMAN_APPROVAL',_utf8mb4'EXTERNAL_EXECUTION',_utf8mb4'COMPENSATING'))) or ((`status` <> _utf8mb4'PENDING') and (`completion_phase` = _utf8mb4'COMPLETED')))),
  CONSTRAINT `ck_flow_instance_current_stage_index` CHECK ((`current_stage_index` <= 9)),
  CONSTRAINT `ck_flow_instance_deadline` CHECK ((((`deadline_policy` is null) and (`deadline_remind_at` is null) and (`deadline_due_at` is null) and (`deadline_reminded_at` is null) and (`deadline_processed_at` is null)) or ((json_type(`deadline_policy`) = _utf8mb4'OBJECT') and (`deadline_due_at` is not null) and ((`deadline_remind_at` is null) or (`deadline_remind_at` < `deadline_due_at`)) and ((`deadline_reminded_at` is null) or (`deadline_remind_at` is not null)) and ((`deadline_processed_at` is null) or (`deadline_processed_at` >= `deadline_due_at`))))),
  CONSTRAINT `ck_flow_instance_decision_comment_policy` CHECK (((`decision_comment_policy` is null) or (json_type(`decision_comment_policy`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_instance_decision_evidence` CHECK (((`decision_evidence_policy` is null) or ((json_type(`decision_evidence_policy`) = _utf8mb4'OBJECT') and (length(`decision_evidence_policy`) <= 2048)))),
  CONSTRAINT `ck_flow_instance_identity` CHECK (((`instance_id` > 0) and (`requester_id` > 0) and (`approver_id` > 0))),
  CONSTRAINT `ck_flow_instance_record_binding` CHECK ((((`module_code` is null) and (`record_id` is null)) or ((`module_code` is not null) and regexp_like(`module_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`record_id` is not null) and (`record_id` > 0)))),
  CONSTRAINT `ck_flow_instance_required_approvals` CHECK (((`required_approvals` between 1 and json_length(`approver_ids_json`)) and (((`approval_mode` = _ascii'ANY') and (`required_approvals` = 1)) or ((`approval_mode` in (_ascii'SEQUENTIAL',_ascii'ALL')) and (`required_approvals` = json_length(`approver_ids_json`))) or (`approval_mode` = _ascii'QUORUM')))),
  CONSTRAINT `ck_flow_instance_start_context` CHECK (((`start_context` is null) or ((json_type(`start_context`) = _utf8mb4'OBJECT') and (json_length(`start_context`) = 6) and (json_contains_path(`start_context`,_utf8mb4'all',_utf8mb4'$.requesterMemberId',_utf8mb4'$.moduleCode',_utf8mb4'$.recordId',_utf8mb4'$.valuesJson',_utf8mb4'$.rootInstanceId',_utf8mb4'$.subflowDepth') = 1) and (json_type(json_extract(`start_context`,_utf8mb4'$.valuesJson')) = _utf8mb4'OBJECT') and (json_length(json_extract(`start_context`,_utf8mb4'$.valuesJson')) <= 200) and (json_type(json_extract(`start_context`,_utf8mb4'$.subflowDepth')) = _utf8mb4'INTEGER') and (((json_type(json_extract(`start_context`,_utf8mb4'$.rootInstanceId')) = _utf8mb4'NULL') and (cast(json_unquote(json_extract(`start_context`,_utf8mb4'$.subflowDepth')) as unsigned) = 0)) or ((json_type(json_extract(`start_context`,_utf8mb4'$.rootInstanceId')) in (_utf8mb4'INTEGER',_utf8mb4'UNSIGNED INTEGER')) and (cast(json_unquote(json_extract(`start_context`,_utf8mb4'$.rootInstanceId')) as unsigned) > 0) and (cast(json_unquote(json_extract(`start_context`,_utf8mb4'$.subflowDepth')) as unsigned) between 1 and 8))) and (length(`start_context`) <= 262144)))),
  CONSTRAINT `ck_flow_instance_state_version` CHECK ((`state_version` between 0 and 2147483647)),
  CONSTRAINT `ck_flow_instance_status` CHECK ((`status` in (_ascii'PENDING',_ascii'APPROVED',_ascii'REJECTED',_ascii'WITHDRAWN',_ascii'TERMINATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_instance`
--

LOCK TABLES `un_flow_instance` WRITE;
/*!40000 ALTER TABLE `un_flow_instance` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_instance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_instance_form_snapshot`
--

DROP TABLE IF EXISTS `un_flow_instance_form_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_instance_form_snapshot` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `node_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `module_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_json` json NOT NULL,
  `initial_form_json` json NOT NULL,
  `snapshot_version` bigint unsigned NOT NULL DEFAULT '0',
  `materialized_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`instance_id`,`node_code`),
  KEY `fk_flow_form_snapshot_extension` (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  CONSTRAINT `fk_flow_form_snapshot_extension` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_extension_version` (`system_id`, `tenant_id`, `definition_id`, `definition_version`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_form_snapshot_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_form_snapshot_codes` CHECK ((regexp_like(`node_code`,_utf8mb4'^[a-z][a-z0-9_.-]{0,63}$') and regexp_like(`module_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$'))),
  CONSTRAINT `ck_flow_form_snapshot_json` CHECK (((json_type(`policy_json`) = _utf8mb4'ARRAY') and (json_type(`initial_form_json`) = _utf8mb4'OBJECT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_instance_form_snapshot`
--

LOCK TABLES `un_flow_instance_form_snapshot` WRITE;
/*!40000 ALTER TABLE `un_flow_instance_form_snapshot` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_instance_form_snapshot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_node_execution`
--

DROP TABLE IF EXISTS `un_flow_node_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_node_execution` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `node_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `node_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `execution_status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_json` json NOT NULL,
  `execution_version` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`instance_id`,`node_code`),
  KEY `fk_flow_node_execution_extension` (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  CONSTRAINT `fk_flow_node_execution_extension` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_extension_version` (`system_id`, `tenant_id`, `definition_id`, `definition_version`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_node_execution_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_node_execution_identity` CHECK (((`updated_by` > 0) and regexp_like(`node_code`,_utf8mb4'^[a-z][a-z0-9_.-]{0,63}$'))),
  CONSTRAINT `ck_flow_node_execution_result` CHECK ((json_type(`result_json`) = _utf8mb4'OBJECT')),
  CONSTRAINT `ck_flow_node_execution_status` CHECK ((`execution_status` in (_utf8mb4'CONTINUED',_utf8mb4'WAITING_HUMAN',_utf8mb4'WAITING_EVENT',_utf8mb4'WAITING_TIMER',_utf8mb4'WAITING_EXTERNAL',_utf8mb4'WAITING_CONFIRMATION',_utf8mb4'COMPLETED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_node_execution`
--

LOCK TABLES `un_flow_node_execution` WRITE;
/*!40000 ALTER TABLE `un_flow_node_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_node_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_node_execution_event`
--

DROP TABLE IF EXISTS `un_flow_node_execution_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_node_execution_event` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `node_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_sequence` int unsigned NOT NULL,
  `from_status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_json` json NOT NULL,
  `result_json` json NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`instance_id`,`node_code`,`event_sequence`),
  CONSTRAINT `fk_flow_node_event_execution` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`, `node_code`) REFERENCES `un_flow_node_execution` (`system_id`, `tenant_id`, `instance_id`, `node_code`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_node_event_identity` CHECK (((`event_sequence` > 0) and (`actor_member_id` > 0))),
  CONSTRAINT `ck_flow_node_event_json` CHECK (((json_type(`input_json`) = _utf8mb4'OBJECT') and (json_type(`result_json`) = _utf8mb4'OBJECT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_node_execution_event`
--

LOCK TABLES `un_flow_node_execution_event` WRITE;
/*!40000 ALTER TABLE `un_flow_node_execution_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_node_execution_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_parallel_branch_execution`
--

DROP TABLE IF EXISTS `un_flow_parallel_branch_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_parallel_branch_execution` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `branch_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `branch_name` varchar(80) NOT NULL,
  `branch_order` tinyint unsigned NOT NULL,
  `approver_id` bigint NOT NULL,
  `approver_ids_json` json NOT NULL,
  `current_step_index` tinyint unsigned NOT NULL DEFAULT '0',
  `current_stage_index` int unsigned NOT NULL DEFAULT '0',
  `approval_mode` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `required_approvals` tinyint unsigned NOT NULL DEFAULT '1',
  `deadline_policy` json DEFAULT NULL,
  `deadline_remind_at` datetime(6) DEFAULT NULL,
  `deadline_due_at` datetime(6) DEFAULT NULL,
  `deadline_reminded_at` datetime(6) DEFAULT NULL,
  `deadline_processed_at` datetime(6) DEFAULT NULL,
  `decision_comment_policy` json DEFAULT NULL,
  `decision_evidence_policy` json DEFAULT NULL,
  `approval_stage_state` json DEFAULT NULL,
  `approved_approver_ids_json` json NOT NULL DEFAULT (json_array()),
  `rejected_approver_ids_json` json NOT NULL DEFAULT (json_array()),
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`instance_id`,`branch_code`),
  UNIQUE KEY `uk_flow_parallel_branch_order` (`system_id`,`tenant_id`,`instance_id`,`branch_order`),
  UNIQUE KEY `uk_flow_parallel_branch_name` (`system_id`,`tenant_id`,`instance_id`,`branch_name`),
  KEY `idx_flow_parallel_branch_task` (`system_id`,`tenant_id`,`status`,`approver_id`,`started_at`,`instance_id`),
  KEY `ix_flow_branch_deadline_poll` (`system_id`,`tenant_id`,`status`,`deadline_processed_at`,`deadline_remind_at`,`deadline_due_at`),
  CONSTRAINT `fk_flow_parallel_branch_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_branch_decision_comment_policy` CHECK (((`decision_comment_policy` is null) or (json_type(`decision_comment_policy`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_flow_branch_decision_evidence` CHECK (((`decision_evidence_policy` is null) or ((json_type(`decision_evidence_policy`) = _utf8mb4'OBJECT') and (length(`decision_evidence_policy`) <= 2048)))),
  CONSTRAINT `ck_flow_parallel_branch_approval_stage_state` CHECK ((((`approval_stage_state` is null) and (`current_stage_index` = 0)) or ((json_type(`approval_stage_state`) = _utf8mb4'ARRAY') and (json_length(`approval_stage_state`) between 1 and 10) and (`current_stage_index` < json_length(`approval_stage_state`)) and (length(`approval_stage_state`) <= 262144)))),
  CONSTRAINT `ck_flow_parallel_branch_completion` CHECK ((((`status` = _ascii'PENDING') and (`completed_at` is null)) or ((`status` <> _ascii'PENDING') and (`completed_at` is not null) and (`completed_at` >= `started_at`)))),
  CONSTRAINT `ck_flow_parallel_branch_current_stage_index` CHECK ((`current_stage_index` <= 9)),
  CONSTRAINT `ck_flow_parallel_branch_deadline` CHECK ((((`deadline_policy` is null) and (`deadline_remind_at` is null) and (`deadline_due_at` is null) and (`deadline_reminded_at` is null) and (`deadline_processed_at` is null)) or ((json_type(`deadline_policy`) = _utf8mb4'OBJECT') and (`deadline_due_at` is not null) and ((`deadline_remind_at` is null) or (`deadline_remind_at` < `deadline_due_at`)) and ((`deadline_reminded_at` is null) or (`deadline_remind_at` is not null)) and ((`deadline_processed_at` is null) or (`deadline_processed_at` >= `deadline_due_at`))))),
  CONSTRAINT `ck_flow_parallel_branch_decisions` CHECK (((json_type(`approved_approver_ids_json`) = _utf8mb4'ARRAY') and (json_type(`rejected_approver_ids_json`) = _utf8mb4'ARRAY') and ((json_length(`approved_approver_ids_json`) + json_length(`rejected_approver_ids_json`)) <= 10) and ((`approval_mode` <> _ascii'SEQUENTIAL') or ((json_length(`approved_approver_ids_json`) = 0) and (json_length(`rejected_approver_ids_json`) = 0))))),
  CONSTRAINT `ck_flow_parallel_branch_identity` CHECK ((regexp_like(`branch_code`,_ascii'^[a-z][a-z0-9_]{0,63}$') and (char_length(trim(`branch_name`)) between 1 and 80) and (`branch_order` between 0 and 4) and (`approver_id` > 0))),
  CONSTRAINT `ck_flow_parallel_branch_route` CHECK (((json_type(`approver_ids_json`) = _utf8mb4'ARRAY') and (json_length(`approver_ids_json`) between 1 and 10) and (`current_step_index` < json_length(`approver_ids_json`)) and (`approval_mode` in (_ascii'SEQUENTIAL',_ascii'ANY',_ascii'ALL',_ascii'QUORUM')) and (`required_approvals` between 1 and json_length(`approver_ids_json`)) and (((`approval_mode` = _ascii'ANY') and (`required_approvals` = 1)) or ((`approval_mode` in (_ascii'SEQUENTIAL',_ascii'ALL')) and (`required_approvals` = json_length(`approver_ids_json`))) or (`approval_mode` = _ascii'QUORUM')))),
  CONSTRAINT `ck_flow_parallel_branch_status` CHECK ((`status` in (_ascii'PENDING',_ascii'APPROVED',_ascii'REJECTED',_ascii'CANCELLED',_ascii'WITHDRAWN',_ascii'TERMINATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_parallel_branch_execution`
--

LOCK TABLES `un_flow_parallel_branch_execution` WRITE;
/*!40000 ALTER TABLE `un_flow_parallel_branch_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_parallel_branch_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_periodic_schedule`
--

DROP TABLE IF EXISTS `un_flow_periodic_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_periodic_schedule` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `requester_id` bigint NOT NULL,
  `interval_minutes` int unsigned NOT NULL,
  `start_at` datetime(6) NOT NULL,
  `next_fire_at` datetime(6) NOT NULL,
  `last_scheduled_at` datetime(6) DEFAULT NULL,
  `last_instance_id` bigint DEFAULT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `pause_reason` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`definition_id`),
  KEY `idx_flow_periodic_due` (`status`,`next_fire_at`,`system_id`,`tenant_id`,`definition_id`),
  KEY `idx_flow_periodic_instance` (`system_id`,`tenant_id`,`last_instance_id`),
  KEY `fk_flow_periodic_version` (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  CONSTRAINT `fk_flow_periodic_instance` FOREIGN KEY (`system_id`, `tenant_id`, `last_instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_periodic_version` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_periodic_interval` CHECK ((`interval_minutes` between 1 and 525600)),
  CONSTRAINT `ck_flow_periodic_last` CHECK ((((`last_scheduled_at` is null) and (`last_instance_id` is null)) or ((`last_scheduled_at` is not null) and (`last_instance_id` is not null)))),
  CONSTRAINT `ck_flow_periodic_scope` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`definition_id` > 0) and (`definition_version` > 0) and (`requester_id` > 0))),
  CONSTRAINT `ck_flow_periodic_status` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`pause_reason` is null)) or ((`status` = _utf8mb4'PAUSED') and (`pause_reason` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_periodic_schedule`
--

LOCK TABLES `un_flow_periodic_schedule` WRITE;
/*!40000 ALTER TABLE `un_flow_periodic_schedule` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_periodic_schedule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_subflow_run`
--

DROP TABLE IF EXISTS `un_flow_subflow_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_subflow_run` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `subflow_run_id` bigint NOT NULL,
  `execution_id` bigint NOT NULL,
  `attempt_number` int unsigned NOT NULL,
  `launch_key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `child_instance_id` bigint NOT NULL,
  `target_definition_id` bigint NOT NULL,
  `target_definition_version` int unsigned NOT NULL,
  `root_instance_id` bigint NOT NULL,
  `subflow_depth` tinyint unsigned NOT NULL,
  `child_status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `launched_at` datetime(6) NOT NULL,
  `terminal_at` datetime(6) DEFAULT NULL,
  `result_code` varchar(24) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_applied_at` datetime(6) DEFAULT NULL,
  `state_version` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`system_id`,`tenant_id`,`subflow_run_id`),
  UNIQUE KEY `uk_flow_subflow_execution_attempt` (`system_id`,`tenant_id`,`execution_id`,`attempt_number`),
  UNIQUE KEY `uk_flow_subflow_launch_key` (`system_id`,`tenant_id`,`launch_key`),
  UNIQUE KEY `uk_flow_subflow_child` (`system_id`,`tenant_id`,`child_instance_id`),
  KEY `idx_flow_subflow_execution` (`system_id`,`tenant_id`,`execution_id`,`attempt_number`),
  KEY `idx_flow_subflow_running` (`system_id`,`tenant_id`,`child_status`,`launched_at`,`subflow_run_id`),
  KEY `idx_flow_subflow_result_due` (`system_id`,`tenant_id`,`result_applied_at`,`terminal_at`,`subflow_run_id`),
  KEY `fk_flow_subflow_root` (`system_id`,`tenant_id`,`root_instance_id`),
  KEY `fk_flow_subflow_target` (`system_id`,`tenant_id`,`target_definition_id`,`target_definition_version`),
  CONSTRAINT `fk_flow_subflow_child` FOREIGN KEY (`system_id`, `tenant_id`, `child_instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_subflow_execution` FOREIGN KEY (`system_id`, `tenant_id`, `execution_id`) REFERENCES `un_flow_completion_execution` (`system_id`, `tenant_id`, `execution_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_subflow_root` FOREIGN KEY (`system_id`, `tenant_id`, `root_instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_subflow_target` FOREIGN KEY (`system_id`, `tenant_id`, `target_definition_id`, `target_definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_subflow_identity` CHECK (((`subflow_run_id` > 0) and (`execution_id` > 0) and (`attempt_number` > 0) and regexp_like(`launch_key`,_utf8mb4'^[0-9a-f]{64}$') and (`child_instance_id` > 0) and (`target_definition_id` > 0) and (`target_definition_version` > 0) and (`root_instance_id` > 0) and (`subflow_depth` between 1 and 8))),
  CONSTRAINT `ck_flow_subflow_result` CHECK (((`child_status` in (_utf8mb4'RUNNING',_utf8mb4'APPROVED_COMPLETED',_utf8mb4'REJECTED',_utf8mb4'WITHDRAWN',_utf8mb4'TERMINATED')) and (((`child_status` = _utf8mb4'RUNNING') and (`terminal_at` is null) and (`result_code` is null) and (`result_applied_at` is null)) or ((`child_status` <> _utf8mb4'RUNNING') and (`terminal_at` is not null) and (`terminal_at` >= `launched_at`) and (`result_code` = `child_status`) and ((`result_applied_at` is null) or (`result_applied_at` >= `terminal_at`))))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_subflow_run`
--

LOCK TABLES `un_flow_subflow_run` WRITE;
/*!40000 ALTER TABLE `un_flow_subflow_run` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_subflow_run` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_trigger_dispatch`
--

DROP TABLE IF EXISTS `un_flow_trigger_dispatch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_trigger_dispatch` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `event_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `definition_id` bigint DEFAULT NULL,
  `definition_version` int unsigned DEFAULT NULL,
  `instance_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`system_id`,`tenant_id`,`event_key`),
  UNIQUE KEY `uk_flow_trigger_dispatch_instance` (`system_id`,`tenant_id`,`instance_id`),
  KEY `idx_flow_trigger_dispatch_definition` (`system_id`,`tenant_id`,`definition_id`,`definition_version`,`created_at`),
  CONSTRAINT `fk_flow_trigger_dispatch_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_trigger_dispatch_version` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_trigger_dispatch_event_key` CHECK ((char_length(trim(`event_key`)) between 1 and 200)),
  CONSTRAINT `ck_flow_trigger_dispatch_result` CHECK ((((`definition_id` is null) and (`definition_version` is null) and (`instance_id` is null)) or ((`definition_id` is not null) and (`definition_id` > 0) and (`definition_version` is not null) and (`definition_version` > 0) and (`instance_id` is not null) and (`instance_id` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_trigger_dispatch`
--

LOCK TABLES `un_flow_trigger_dispatch` WRITE;
/*!40000 ALTER TABLE `un_flow_trigger_dispatch` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_trigger_dispatch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_trigger_dispatch_instance`
--

DROP TABLE IF EXISTS `un_flow_trigger_dispatch_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_trigger_dispatch_instance` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `event_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `ordinal` int unsigned NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version` int unsigned NOT NULL,
  `instance_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`system_id`,`tenant_id`,`event_key`,`ordinal`),
  UNIQUE KEY `uk_flow_trigger_dispatch_item_instance` (`system_id`,`tenant_id`,`instance_id`),
  KEY `idx_flow_trigger_dispatch_item_version` (`system_id`,`tenant_id`,`definition_id`,`definition_version`),
  CONSTRAINT `fk_flow_trigger_dispatch_item_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_trigger_dispatch_item_parent` FOREIGN KEY (`system_id`, `tenant_id`, `event_key`) REFERENCES `un_flow_trigger_dispatch` (`system_id`, `tenant_id`, `event_key`) ON DELETE RESTRICT,
  CONSTRAINT `fk_flow_trigger_dispatch_item_version` FOREIGN KEY (`system_id`, `tenant_id`, `definition_id`, `definition_version`) REFERENCES `un_flow_definition_version` (`system_id`, `tenant_id`, `definition_id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_trigger_dispatch_item_event_key` CHECK ((char_length(trim(`event_key`)) between 1 and 200)),
  CONSTRAINT `ck_flow_trigger_dispatch_item_identity` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`definition_id` > 0) and (`definition_version` > 0) and (`instance_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_trigger_dispatch_instance`
--

LOCK TABLES `un_flow_trigger_dispatch_instance` WRITE;
/*!40000 ALTER TABLE `un_flow_trigger_dispatch_instance` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_trigger_dispatch_instance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_flow_urge`
--

DROP TABLE IF EXISTS `un_flow_urge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_flow_urge` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `urge_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `actor_id` bigint NOT NULL,
  `recipient_id` bigint NOT NULL,
  `message` varchar(500) NOT NULL DEFAULT '',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`urge_id`),
  KEY `idx_flow_urge_instance_page` (`system_id`,`tenant_id`,`instance_id`,`created_at`,`urge_id`),
  CONSTRAINT `fk_flow_urge_instance` FOREIGN KEY (`system_id`, `tenant_id`, `instance_id`) REFERENCES `un_flow_instance` (`system_id`, `tenant_id`, `instance_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_flow_urge_identity` CHECK (((`urge_id` > 0) and (`actor_id` > 0) and (`recipient_id` > 0))),
  CONSTRAINT `ck_flow_urge_message` CHECK (((`message` = trim(`message`)) and (char_length(`message`) between 0 and 500)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_flow_urge`
--

LOCK TABLES `un_flow_urge` WRITE;
/*!40000 ALTER TABLE `un_flow_urge` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_flow_urge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_action`
--

DROP TABLE IF EXISTS `un_module_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_action` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `module_id` bigint NOT NULL,
  `action_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `placement` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_code` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `confirm_message` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `property_json` json NOT NULL,
  `code_locked_at` datetime(3) DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_action_module_code` (`system_id`,`module_id`,`action_code`),
  UNIQUE KEY `uk_module_action_permission` (`system_id`,`permission_code`),
  UNIQUE KEY `uk_module_action_system_id` (`system_id`,`id`),
  KEY `idx_module_action_list` (`system_id`,`module_id`,`desired_status`,`placement`,`sort_order`,`id`),
  CONSTRAINT `fk_module_action_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `ck_module_action_code` CHECK (regexp_like(`action_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_action_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_action_placement` CHECK ((`placement` in (_utf8mb4'TOOLBAR',_utf8mb4'ROW',_utf8mb4'DETAIL',_utf8mb4'BATCH'))),
  CONSTRAINT `ck_module_action_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_action_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_module_action_type` CHECK ((`action_type` in (_utf8mb4'CREATE',_utf8mb4'UPDATE',_utf8mb4'DELETE',_utf8mb4'CUSTOM',_utf8mb4'APPROVAL',_utf8mb4'IMPORT',_utf8mb4'EXPORT',_utf8mb4'PRINT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_action`
--

LOCK TABLES `un_module_action` WRITE;
/*!40000 ALTER TABLE `un_module_action` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_action` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_ai_fill_history`
--

DROP TABLE IF EXISTS `un_module_ai_fill_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_ai_fill_history` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `record_version` bigint unsigned NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `field_snapshot_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_schema` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_version_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confidence` decimal(5,4) NOT NULL,
  `actor_member_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `model_snapshot` varchar(160) NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `proposal_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `previous_value_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_value_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `materialization_version` bigint unsigned DEFAULT NULL,
  `outcome` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_ai_fill_history_id` (`id`),
  UNIQUE KEY `uk_module_ai_fill_history_proposal` (`system_id`,`tenant_id`,`proposal_id`),
  KEY `idx_module_ai_fill_history_record` (`system_id`,`tenant_id`,`record_id`,`field_snapshot_id`,`created_at`,`id`),
  CONSTRAINT `ck_module_ai_fill_history_confidence` CHECK ((`confidence` between 0.5000 and 1.0000)),
  CONSTRAINT `ck_module_ai_fill_history_field` CHECK ((regexp_like(`field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`result_schema` in (_utf8mb4'STRING',_utf8mb4'DECIMAL',_utf8mb4'INTEGER',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'BOOLEAN')))),
  CONSTRAINT `ck_module_ai_fill_history_hash` CHECK ((regexp_like(`source_version_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`previous_value_hash` is null) or regexp_like(`previous_value_hash`,_utf8mb4'^[0-9a-f]{64}$')) and ((`result_value_hash` is null) or regexp_like(`result_value_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_module_ai_fill_history_identity` CHECK (((`id` > 0) and (`record_id` > 0) and (`record_version` >= 0) and (`schema_version_id` > 0) and (`module_snapshot_id` > 0) and (`field_snapshot_id` > 0) and (`logical_field_id` > 0) and (`actor_member_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`policy_version_id` > 0))),
  CONSTRAINT `ck_module_ai_fill_history_outcome` CHECK ((((`outcome` = _utf8mb4'CREATED') and (`previous_value_hash` is null) and (`result_value_hash` is not null) and (`materialization_version` = 0)) or ((`outcome` = _utf8mb4'OVERWRITTEN') and (`previous_value_hash` is not null) and (`result_value_hash` is not null) and (`materialization_version` > 0)) or ((`outcome` = _utf8mb4'REJECTED') and (`result_value_hash` is null) and (`materialization_version` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_ai_fill_history`
--

LOCK TABLES `un_module_ai_fill_history` WRITE;
/*!40000 ALTER TABLE `un_module_ai_fill_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_ai_fill_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_ai_fill_materialization`
--

DROP TABLE IF EXISTS `un_module_ai_fill_materialization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_ai_fill_materialization` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `field_snapshot_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_schema` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `string_value` text,
  `decimal_value` decimal(38,18) DEFAULT NULL,
  `date_value` date DEFAULT NULL,
  `datetime_value` datetime(6) DEFAULT NULL,
  `boolean_value` tinyint(1) DEFAULT NULL,
  `display_value` text NOT NULL,
  `value_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_version_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confidence` decimal(5,4) NOT NULL,
  `materialized_by_member_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `model_snapshot` varchar(160) NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `proposal_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`record_id`,`field_snapshot_id`),
  UNIQUE KEY `uk_module_ai_fill_materialization_id` (`id`),
  KEY `idx_module_ai_fill_field` (`system_id`,`tenant_id`,`field_snapshot_id`,`updated_at`,`record_id`),
  CONSTRAINT `ck_module_ai_fill_materialization_confidence` CHECK ((`confidence` between 0.5000 and 1.0000)),
  CONSTRAINT `ck_module_ai_fill_materialization_field` CHECK ((regexp_like(`field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (`result_schema` in (_utf8mb4'STRING',_utf8mb4'DECIMAL',_utf8mb4'INTEGER',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'BOOLEAN')))),
  CONSTRAINT `ck_module_ai_fill_materialization_hash` CHECK ((regexp_like(`value_hash`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`source_version_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_module_ai_fill_materialization_identity` CHECK (((`id` > 0) and (`record_id` > 0) and (`schema_version_id` > 0) and (`module_snapshot_id` > 0) and (`field_snapshot_id` > 0) and (`logical_field_id` > 0) and (`materialized_by_member_id` > 0) and (`provider_id` > 0) and (`policy_version_id` > 0) and (`provider_version` >= 0) and (`version` >= 0))),
  CONSTRAINT `ck_module_ai_fill_materialization_time` CHECK ((`updated_at` >= `created_at`)),
  CONSTRAINT `ck_module_ai_fill_materialization_typed` CHECK ((((`result_schema` = _utf8mb4'STRING') and (`string_value` is not null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is null)) or ((`result_schema` in (_utf8mb4'DECIMAL',_utf8mb4'INTEGER')) and (`string_value` is null) and (`decimal_value` is not null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is null)) or ((`result_schema` = _utf8mb4'DATE') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is not null) and (`datetime_value` is null) and (`boolean_value` is null)) or ((`result_schema` = _utf8mb4'DATETIME') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is not null) and (`boolean_value` is null)) or ((`result_schema` = _utf8mb4'BOOLEAN') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_ai_fill_materialization`
--

LOCK TABLES `un_module_ai_fill_materialization` WRITE;
/*!40000 ALTER TABLE `un_module_ai_fill_materialization` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_ai_fill_materialization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_ai_generated_draft_execution`
--

DROP TABLE IF EXISTS `un_module_ai_generated_draft_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_ai_generated_draft_execution` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `proposal_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `turn_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `operation` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `prepare_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `prepare_trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execute_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execute_trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_json` mediumtext,
  `created_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_ai_generated_execution_id` (`id`),
  UNIQUE KEY `uk_module_ai_generated_execution_proposal` (`system_id`,`tenant_id`,`member_id`,`proposal_id`),
  UNIQUE KEY `uk_module_ai_generated_execution_key` (`system_id`,`tenant_id`,`member_id`,`idempotency_key`),
  KEY `fk_module_ai_generated_execution_member` (`system_id`,`member_id`,`tenant_id`),
  CONSTRAINT `fk_module_ai_generated_execution_member` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_ai_generated_execution_hash` CHECK (regexp_like(`payload_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_module_ai_generated_execution_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`member_id` > 0) and (`account_id` > 0) and (`authorization_epoch` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0))),
  CONSTRAINT `ck_module_ai_generated_execution_operation` CHECK ((`operation` in (_utf8mb4'CONFIG_REPORT_DRAFT',_utf8mb4'CONFIG_PRINT_TEMPLATE_DRAFT'))),
  CONSTRAINT `ck_module_ai_generated_execution_result` CHECK (((((`result_json` is null) and (`completed_at` is null)) or ((`result_json` is not null) and (`completed_at` is not null))) and ((`result_json` is null) or json_valid(`result_json`)))),
  CONSTRAINT `ck_module_ai_generated_execution_time` CHECK (((`expires_at` > `created_at`) and ((`completed_at` is null) or (`completed_at` >= `created_at`)))),
  CONSTRAINT `ck_module_ai_generated_execution_tokens` CHECK ((regexp_like(`proposal_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`session_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`turn_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`idempotency_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_ai_generated_draft_execution`
--

LOCK TABLES `un_module_ai_generated_draft_execution` WRITE;
/*!40000 ALTER TABLE `un_module_ai_generated_draft_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_ai_generated_draft_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_auto_number_sequence`
--

DROP TABLE IF EXISTS `un_module_auto_number_sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_auto_number_sequence` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `next_value` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`),
  KEY `idx_auto_number_field` (`system_id`,`logical_module_id`,`logical_field_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_auto_number_sequence`
--

LOCK TABLES `un_module_auto_number_sequence` WRITE;
/*!40000 ALTER TABLE `un_module_auto_number_sequence` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_auto_number_sequence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_config_check`
--

DROP TABLE IF EXISTS `un_module_config_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_config_check` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `base_version_id` bigint DEFAULT NULL,
  `draft_revision` bigint NOT NULL,
  `draft_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `blocker_count` int NOT NULL DEFAULT '0',
  `warning_count` int NOT NULL DEFAULT '0',
  `snapshot_size_bytes` bigint NOT NULL DEFAULT '0',
  `report_json` json DEFAULT NULL,
  `started_at` datetime(3) NOT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `expires_at` datetime(3) NOT NULL,
  `checked_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_config_check_system_id` (`system_id`,`id`),
  KEY `idx_module_config_check_list` (`system_id`,`started_at` DESC,`id`),
  KEY `idx_module_config_check_status` (`system_id`,`status`,`expires_at`),
  KEY `fk_module_config_check_base` (`system_id`,`base_version_id`),
  CONSTRAINT `fk_module_config_check_base` FOREIGN KEY (`system_id`, `base_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_check_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_module_config_check_counts` CHECK (((`blocker_count` >= 0) and (`warning_count` >= 0) and (`snapshot_size_bytes` >= 0))),
  CONSTRAINT `ck_module_config_check_revision` CHECK ((`draft_revision` >= 0)),
  CONSTRAINT `ck_module_config_check_status` CHECK ((`status` in (_utf8mb4'RUNNING',_utf8mb4'PASSED',_utf8mb4'FAILED',_utf8mb4'STALE'))),
  CONSTRAINT `ck_module_config_check_times` CHECK (((`expires_at` > `started_at`) and ((`completed_at` is null) or (`completed_at` >= `started_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_config_check`
--

LOCK TABLES `un_module_config_check` WRITE;
/*!40000 ALTER TABLE `un_module_config_check` DISABLE KEYS */;
INSERT INTO `un_module_config_check` VALUES (2085922246542835714,2085920203721703426,NULL,0,'0b736f0bc155c35b5af0665581238d91e88a2a275e31f5371bba38886c002996','FAILED',1,0,235,'{\"blockerCount\": 1, \"warningCount\": 0, \"snapshotChecksum\": \"0b736f0bc155c35b5af0665581238d91e88a2a275e31f5371bba38886c002996\", \"snapshotSizeBytes\": 235}','2026-08-08 02:53:12.795','2026-08-08 02:53:12.795','2026-08-08 03:03:12.795',2085917350597300225,0);
/*!40000 ALTER TABLE `un_module_config_check` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_config_check_issue`
--

DROP TABLE IF EXISTS `un_module_config_check_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_config_check_issue` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `check_id` bigint NOT NULL,
  `severity` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `issue_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resource_id` bigint DEFAULT NULL,
  `property_path` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issue_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `suggested_action` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_module_check_issue_check` (`system_id`,`check_id`,`severity`,`property_path`,`id`),
  KEY `idx_module_check_issue_resource` (`system_id`,`resource_type`,`resource_id`),
  CONSTRAINT `fk_module_check_issue_check` FOREIGN KEY (`system_id`, `check_id`) REFERENCES `un_module_config_check` (`system_id`, `id`),
  CONSTRAINT `ck_module_check_issue_resource` CHECK (((`resource_type` is null) or (`resource_type` in (_utf8mb4'ROOT',_utf8mb4'GROUP',_utf8mb4'MODULE',_utf8mb4'FIELD',_utf8mb4'DICTIONARY',_utf8mb4'DICTIONARY_ITEM',_utf8mb4'PAGE',_utf8mb4'COMPONENT',_utf8mb4'ACTION',_utf8mb4'RULE',_utf8mb4'PERMISSION')))),
  CONSTRAINT `ck_module_check_issue_severity` CHECK ((`severity` in (_utf8mb4'BLOCKER',_utf8mb4'WARNING')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_config_check_issue`
--

LOCK TABLES `un_module_config_check_issue` WRITE;
/*!40000 ALTER TABLE `un_module_config_check_issue` DISABLE KEYS */;
INSERT INTO `un_module_config_check_issue` VALUES (2085922246618333185,2085920203721703426,2085922246542835714,'BLOCKER','NO_ENABLED_MODULE','ROOT',NULL,'modules','','','2026-08-08 02:53:12.795');
/*!40000 ALTER TABLE `un_module_config_check_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_config_reference`
--

DROP TABLE IF EXISTS `un_module_config_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_config_reference` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `source_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_id` bigint NOT NULL,
  `target_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `relation_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `property_path` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `source_module_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'MODULE') then `source_id` end)) STORED,
  `source_field_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'FIELD') then `source_id` end)) STORED,
  `source_page_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'PAGE') then `source_id` end)) STORED,
  `source_component_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'COMPONENT') then `source_id` end)) STORED,
  `source_action_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'ACTION') then `source_id` end)) STORED,
  `source_rule_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'RULE') then `source_id` end)) STORED,
  `source_dictionary_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'DICTIONARY') then `source_id` end)) STORED,
  `source_dictionary_item_id` bigint GENERATED ALWAYS AS ((case when (`source_type` = _utf8mb4'DICTIONARY_ITEM') then `source_id` end)) STORED,
  `target_module_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'MODULE') then `target_id` end)) STORED,
  `target_field_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'FIELD') then `target_id` end)) STORED,
  `target_page_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'PAGE') then `target_id` end)) STORED,
  `target_action_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'ACTION') then `target_id` end)) STORED,
  `target_rule_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'RULE') then `target_id` end)) STORED,
  `target_dictionary_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'DICTIONARY') then `target_id` end)) STORED,
  `target_dictionary_item_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'DICTIONARY_ITEM') then `target_id` end)) STORED,
  `target_permission_id` bigint GENERATED ALWAYS AS ((case when (`target_type` = _utf8mb4'PERMISSION') then `target_id` end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_config_reference` (`system_id`,`source_type`,`source_id`,`target_type`,`target_id`,`relation_type`),
  KEY `idx_module_config_reference_target` (`system_id`,`target_type`,`target_id`,`source_type`,`source_id`),
  KEY `idx_module_config_reference_source` (`system_id`,`source_type`,`source_id`),
  KEY `fk_module_config_ref_source_module` (`system_id`,`source_module_id`),
  KEY `fk_module_config_ref_source_field` (`system_id`,`source_field_id`),
  KEY `fk_module_config_ref_source_page` (`system_id`,`source_page_id`),
  KEY `fk_module_config_ref_source_component` (`system_id`,`source_component_id`),
  KEY `fk_module_config_ref_source_action` (`system_id`,`source_action_id`),
  KEY `fk_module_config_ref_source_rule` (`system_id`,`source_rule_id`),
  KEY `fk_module_config_ref_source_dictionary` (`system_id`,`source_dictionary_id`),
  KEY `fk_module_config_ref_source_dictionary_item` (`system_id`,`source_dictionary_item_id`),
  KEY `fk_module_config_ref_target_module` (`system_id`,`target_module_id`),
  KEY `fk_module_config_ref_target_field` (`system_id`,`target_field_id`),
  KEY `fk_module_config_ref_target_page` (`system_id`,`target_page_id`),
  KEY `fk_module_config_ref_target_action` (`system_id`,`target_action_id`),
  KEY `fk_module_config_ref_target_rule` (`system_id`,`target_rule_id`),
  KEY `fk_module_config_ref_target_dictionary` (`system_id`,`target_dictionary_id`),
  KEY `fk_module_config_ref_target_dictionary_item` (`system_id`,`target_dictionary_item_id`),
  KEY `fk_module_config_ref_target_permission` (`target_permission_id`),
  KEY `fk_module_config_ref_target_permission_system` (`system_id`,`target_permission_id`),
  CONSTRAINT `fk_module_config_ref_source_action` FOREIGN KEY (`system_id`, `source_action_id`) REFERENCES `un_module_action` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_source_component` FOREIGN KEY (`system_id`, `source_component_id`) REFERENCES `un_module_page_component` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_source_dictionary` FOREIGN KEY (`system_id`, `source_dictionary_id`) REFERENCES `un_module_dictionary` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_source_dictionary_item` FOREIGN KEY (`system_id`, `source_dictionary_item_id`) REFERENCES `un_module_dictionary_item` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_source_field` FOREIGN KEY (`system_id`, `source_field_id`) REFERENCES `un_module_field` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_source_module` FOREIGN KEY (`system_id`, `source_module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_source_page` FOREIGN KEY (`system_id`, `source_page_id`) REFERENCES `un_module_page` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_source_rule` FOREIGN KEY (`system_id`, `source_rule_id`) REFERENCES `un_module_rule` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_action` FOREIGN KEY (`system_id`, `target_action_id`) REFERENCES `un_module_action` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_dictionary` FOREIGN KEY (`system_id`, `target_dictionary_id`) REFERENCES `un_module_dictionary` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_dictionary_item` FOREIGN KEY (`system_id`, `target_dictionary_item_id`) REFERENCES `un_module_dictionary_item` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_field` FOREIGN KEY (`system_id`, `target_field_id`) REFERENCES `un_module_field` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_module` FOREIGN KEY (`system_id`, `target_module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_page` FOREIGN KEY (`system_id`, `target_page_id`) REFERENCES `un_module_page` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_permission_system` FOREIGN KEY (`system_id`, `target_permission_id`) REFERENCES `un_plat_permission` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_ref_target_rule` FOREIGN KEY (`system_id`, `target_rule_id`) REFERENCES `un_module_rule` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_reference_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_module_config_reference_relation` CHECK (regexp_like(`relation_type`,_utf8mb4'^[A-Z][A-Z0-9_]{1,31}$')),
  CONSTRAINT `ck_module_config_reference_self` CHECK (((`source_type` <> `target_type`) or (`source_id` <> `target_id`))),
  CONSTRAINT `ck_module_config_reference_source` CHECK ((`source_type` in (_utf8mb4'MODULE',_utf8mb4'FIELD',_utf8mb4'PAGE',_utf8mb4'COMPONENT',_utf8mb4'ACTION',_utf8mb4'RULE',_utf8mb4'DICTIONARY',_utf8mb4'DICTIONARY_ITEM'))),
  CONSTRAINT `ck_module_config_reference_target` CHECK ((`target_type` in (_utf8mb4'MODULE',_utf8mb4'FIELD',_utf8mb4'PAGE',_utf8mb4'ACTION',_utf8mb4'RULE',_utf8mb4'DICTIONARY',_utf8mb4'DICTIONARY_ITEM',_utf8mb4'PERMISSION')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_config_reference`
--

LOCK TABLES `un_module_config_reference` WRITE;
/*!40000 ALTER TABLE `un_module_config_reference` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_config_reference` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_config_root`
--

DROP TABLE IF EXISTS `un_module_config_root`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_config_root` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLEAN',
  `draft_revision` bigint NOT NULL DEFAULT '0',
  `draft_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `base_version_id` bigint DEFAULT NULL,
  `last_check_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_config_root_system` (`system_id`),
  UNIQUE KEY `uk_module_config_root_system_id` (`system_id`,`id`),
  KEY `idx_module_config_root_active` (`system_id`,`active_version_id`),
  KEY `fk_module_config_root_base` (`system_id`,`base_version_id`),
  KEY `fk_module_config_root_check` (`system_id`,`last_check_id`),
  CONSTRAINT `fk_module_config_root_active` FOREIGN KEY (`system_id`, `active_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_root_base` FOREIGN KEY (`system_id`, `base_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_root_check` FOREIGN KEY (`system_id`, `last_check_id`) REFERENCES `un_module_config_check` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_root_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_module_config_root_revision` CHECK ((`draft_revision` >= 0)),
  CONSTRAINT `ck_module_config_root_status` CHECK ((`status` in (_utf8mb4'CLEAN',_utf8mb4'DIRTY',_utf8mb4'CHECKING',_utf8mb4'CHECK_FAILED',_utf8mb4'CHECKED',_utf8mb4'PUBLISHING'))),
  CONSTRAINT `ck_module_config_root_versions` CHECK ((((`active_version_id` is null) and (`base_version_id` is null)) or ((`active_version_id` is not null) and (`base_version_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_config_root`
--

LOCK TABLES `un_module_config_root` WRITE;
/*!40000 ALTER TABLE `un_module_config_root` DISABLE KEYS */;
INSERT INTO `un_module_config_root` VALUES (2085922246257623042,2085920203721703426,'CHECK_FAILED',0,'0b736f0bc155c35b5af0665581238d91e88a2a275e31f5371bba38886c002996',NULL,NULL,2085922246542835714,'2026-08-08 02:53:12.724',2085917350597300225,'2026-08-08 02:53:12.795',2085917350597300225,2);
/*!40000 ALTER TABLE `un_module_config_root` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_config_version`
--

DROP TABLE IF EXISTS `un_module_config_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_config_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `version_no` bigint NOT NULL,
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `based_on_version_id` bigint DEFAULT NULL,
  `rollback_target_version_id` bigint DEFAULT NULL,
  `source_check_id` bigint NOT NULL,
  `snapshot_json` json NOT NULL,
  `snapshot_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `snapshot_size_bytes` bigint NOT NULL,
  `impact_report_json` json NOT NULL,
  `published_at` datetime(3) NOT NULL,
  `published_by` bigint NOT NULL,
  `publish_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_config_version_no` (`system_id`,`version_no`),
  UNIQUE KEY `uk_module_config_version_system_id` (`system_id`,`id`),
  KEY `idx_module_config_version_time` (`system_id`,`published_at` DESC,`id`),
  KEY `idx_module_config_version_checksum` (`system_id`,`snapshot_checksum`),
  KEY `fk_module_config_version_base` (`system_id`,`based_on_version_id`),
  KEY `fk_module_config_version_rollback` (`system_id`,`rollback_target_version_id`),
  KEY `fk_module_config_version_check` (`system_id`,`source_check_id`),
  CONSTRAINT `fk_module_config_version_base` FOREIGN KEY (`system_id`, `based_on_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_version_check` FOREIGN KEY (`system_id`, `source_check_id`) REFERENCES `un_module_config_check` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_version_rollback` FOREIGN KEY (`system_id`, `rollback_target_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `fk_module_config_version_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_module_config_version_no` CHECK ((`version_no` > 0)),
  CONSTRAINT `ck_module_config_version_rollback` CHECK ((((`source_type` = _utf8mb4'PUBLISH') and (`rollback_target_version_id` is null)) or ((`source_type` = _utf8mb4'ROLLBACK') and (`rollback_target_version_id` is not null)))),
  CONSTRAINT `ck_module_config_version_size` CHECK (((`snapshot_size_bytes` >= 2) and (`snapshot_size_bytes` <= 2097152))),
  CONSTRAINT `ck_module_config_version_source` CHECK ((`source_type` in (_utf8mb4'PUBLISH',_utf8mb4'ROLLBACK')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_config_version`
--

LOCK TABLES `un_module_config_version` WRITE;
/*!40000 ALTER TABLE `un_module_config_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_config_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_dashboard`
--

DROP TABLE IF EXISTS `un_module_dashboard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_dashboard` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `dashboard_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `placement` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `system_home_singleton` tinyint GENERATED ALWAYS AS ((case when (`placement` = _ascii'SYSTEM_HOME') then 1 else NULL end)) STORED,
  `dashboard_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `draft_json` json NOT NULL,
  `draft_version` bigint NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `active_version_no` int unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_dashboard_code` (`system_id`,`tenant_id`,`dashboard_code`),
  UNIQUE KEY `uk_module_dashboard_active_identity` (`system_id`,`tenant_id`,`id`,`active_version_id`,`active_version_no`),
  UNIQUE KEY `uk_module_dashboard_system_home` (`system_id`,`tenant_id`,`system_home_singleton`),
  KEY `idx_module_dashboard_list` (`system_id`,`tenant_id`,`updated_at` DESC,`id` DESC),
  KEY `idx_module_dashboard_active` (`system_id`,`tenant_id`,`active_version_id`),
  CONSTRAINT `fk_module_dashboard_active_version` FOREIGN KEY (`system_id`, `tenant_id`, `id`, `active_version_id`, `active_version_no`) REFERENCES `un_module_dashboard_version` (`system_id`, `tenant_id`, `dashboard_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_dashboard_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_dashboard_active` CHECK ((((`active_version_id` is null) and (`active_version_no` is null)) or ((`active_version_id` > 0) and (`active_version_no` > 0)))),
  CONSTRAINT `ck_module_dashboard_code` CHECK (regexp_like(`dashboard_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_module_dashboard_description` CHECK (((`description` is null) or (char_length(trim(`description`)) between 1 and 2000))),
  CONSTRAINT `ck_module_dashboard_draft` CHECK (((`draft_version` > 0) and (json_type(`draft_json`) = _utf8mb4'OBJECT') and (length(`draft_json`) between 2 and 262144))),
  CONSTRAINT `ck_module_dashboard_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0))),
  CONSTRAINT `ck_module_dashboard_name` CHECK ((char_length(trim(`dashboard_name`)) between 1 and 200)),
  CONSTRAINT `ck_module_dashboard_placement` CHECK ((`placement` in (_utf8mb4'SYSTEM_HOME',_utf8mb4'APPLICATION_HOME',_utf8mb4'MODULE_HOME',_utf8mb4'PERSONAL_HOME'))),
  CONSTRAINT `ck_module_dashboard_state` CHECK (((`updated_at` >= `created_at`) and (`version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_dashboard`
--

LOCK TABLES `un_module_dashboard` WRITE;
/*!40000 ALTER TABLE `un_module_dashboard` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_dashboard` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_dashboard_scope_binding`
--

DROP TABLE IF EXISTS `un_module_dashboard_scope_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_dashboard_scope_binding` (
  `dashboard_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `scope_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scope_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_member_id` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`dashboard_id`),
  UNIQUE KEY `uk_module_dashboard_scope_context` (`system_id`,`tenant_id`,`scope_type`,`scope_key`,`owner_member_id`),
  KEY `idx_module_dashboard_personal` (`system_id`,`tenant_id`,`owner_member_id`,`scope_key`,`dashboard_id`),
  CONSTRAINT `fk_module_dashboard_scope_root` FOREIGN KEY (`system_id`, `tenant_id`, `dashboard_id`) REFERENCES `un_module_dashboard` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_dashboard_scope_identity` CHECK (((`dashboard_id` > 0) and (`system_id` > 0) and (`tenant_id` > 0))),
  CONSTRAINT `ck_module_dashboard_scope_key` CHECK (regexp_like(`scope_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
  CONSTRAINT `ck_module_dashboard_scope_owner` CHECK ((((`scope_type` in (_utf8mb4'APPLICATION_HOME',_utf8mb4'MODULE_HOME')) and (`owner_member_id` = 0)) or ((`scope_type` = _utf8mb4'PERSONAL_HOME') and (`owner_member_id` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_dashboard_scope_binding`
--

LOCK TABLES `un_module_dashboard_scope_binding` WRITE;
/*!40000 ALTER TABLE `un_module_dashboard_scope_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_dashboard_scope_binding` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_dashboard_version`
--

DROP TABLE IF EXISTS `un_module_dashboard_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_dashboard_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `source_draft_version` bigint NOT NULL,
  `dashboard_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `placement` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `dashboard_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `snapshot_json` json NOT NULL,
  `snapshot_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `widget_count` tinyint unsigned NOT NULL,
  `published_by_member_id` bigint NOT NULL,
  `published_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`dashboard_id`,`id`),
  UNIQUE KEY `uk_module_dashboard_version_id` (`system_id`,`tenant_id`,`dashboard_id`,`id`,`version_no`),
  UNIQUE KEY `uk_module_dashboard_version_no` (`system_id`,`tenant_id`,`dashboard_id`,`version_no`),
  KEY `idx_module_dashboard_version_list` (`system_id`,`tenant_id`,`dashboard_id`,`version_no` DESC),
  KEY `idx_module_dashboard_version_fingerprint` (`system_id`,`tenant_id`,`dashboard_id`,`snapshot_fingerprint`,`version_no` DESC),
  KEY `fk_module_dashboard_version_publisher` (`system_id`,`published_by_member_id`),
  CONSTRAINT `fk_module_dashboard_version_publisher` FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_dashboard_version_root` FOREIGN KEY (`system_id`, `tenant_id`, `dashboard_id`) REFERENCES `un_module_dashboard` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_dashboard_version_code` CHECK (regexp_like(`dashboard_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_module_dashboard_version_identity` CHECK (((`id` > 0) and (`dashboard_id` > 0) and (`version_no` > 0) and (`source_draft_version` > 0) and (`published_by_member_id` > 0))),
  CONSTRAINT `ck_module_dashboard_version_name` CHECK (((char_length(trim(`dashboard_name`)) between 1 and 200) and ((`description` is null) or (char_length(trim(`description`)) between 1 and 2000)))),
  CONSTRAINT `ck_module_dashboard_version_placement` CHECK ((`placement` in (_utf8mb4'SYSTEM_HOME',_utf8mb4'APPLICATION_HOME',_utf8mb4'MODULE_HOME',_utf8mb4'PERSONAL_HOME'))),
  CONSTRAINT `ck_module_dashboard_version_snapshot` CHECK (((json_type(`snapshot_json`) = _utf8mb4'OBJECT') and (length(`snapshot_json`) between 2 and 262144) and regexp_like(`snapshot_fingerprint`,_ascii'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_module_dashboard_version_widgets` CHECK ((`widget_count` between 1 and 20))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_dashboard_version`
--

LOCK TABLES `un_module_dashboard_version` WRITE;
/*!40000 ALTER TABLE `un_module_dashboard_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_dashboard_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_dashboard_version_widget`
--

DROP TABLE IF EXISTS `un_module_dashboard_version_widget`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_dashboard_version_widget` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  `dashboard_version_id` bigint NOT NULL,
  `dashboard_version_no` int unsigned NOT NULL,
  `widget_ordinal` tinyint unsigned NOT NULL,
  `widget_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `widget_type` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `widget_title` varchar(200) NOT NULL,
  `data_source_id` bigint DEFAULT NULL,
  `data_source_version_id` bigint DEFAULT NULL,
  `data_source_version_no` int unsigned DEFAULT NULL,
  `data_source_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `module_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `schema_version_id` varchar(200) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `row_limit` tinyint unsigned DEFAULT NULL,
  `stat_aggregation` varchar(8) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_measure_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_measure_field_id` bigint DEFAULT NULL,
  `stat_measure_field_name` varchar(200) DEFAULT NULL,
  `stat_measure_field_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_measure_query_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_group_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_group_field_id` bigint DEFAULT NULL,
  `stat_group_field_name` varchar(200) DEFAULT NULL,
  `stat_group_field_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_group_query_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_group_limit` tinyint unsigned DEFAULT NULL,
  `stat_time_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_time_field_id` bigint DEFAULT NULL,
  `stat_time_field_name` varchar(200) DEFAULT NULL,
  `stat_time_field_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_time_query_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_time_grain` varchar(8) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stat_time_start` date DEFAULT NULL,
  `stat_time_end` date DEFAULT NULL,
  `kpi_id` bigint DEFAULT NULL,
  `kpi_version_id` bigint DEFAULT NULL,
  `kpi_version_no` int unsigned DEFAULT NULL,
  `kpi_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `kpi_name` varchar(200) DEFAULT NULL,
  `kpi_subject_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `kpi_period_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `refresh_seconds` smallint unsigned NOT NULL DEFAULT '0',
  `click_through` varchar(300) DEFAULT NULL,
  `style_variant` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'STANDARD',
  `grid_x` tinyint unsigned NOT NULL,
  `grid_y` smallint unsigned NOT NULL,
  `grid_width` tinyint unsigned NOT NULL,
  `grid_height` smallint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`dashboard_id`,`dashboard_version_id`,`id`),
  UNIQUE KEY `uk_module_dashboard_widget_ordinal` (`system_id`,`tenant_id`,`dashboard_id`,`dashboard_version_id`,`widget_ordinal`),
  UNIQUE KEY `uk_module_dashboard_widget_code` (`system_id`,`tenant_id`,`dashboard_id`,`dashboard_version_id`,`widget_code`),
  KEY `idx_module_dashboard_widget_source` (`system_id`,`tenant_id`,`data_source_id`,`data_source_version_id`,`data_source_version_no`),
  KEY `fk_module_dashboard_widget_version` (`system_id`,`tenant_id`,`dashboard_id`,`dashboard_version_id`,`dashboard_version_no`),
  KEY `idx_module_dashboard_widget_kpi` (`system_id`,`tenant_id`,`kpi_id`,`kpi_version_id`,`kpi_version_no`),
  CONSTRAINT `fk_module_dashboard_widget_kpi_version` FOREIGN KEY (`system_id`, `tenant_id`, `kpi_id`, `kpi_version_id`, `kpi_version_no`) REFERENCES `un_module_kpi_version` (`system_id`, `tenant_id`, `kpi_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_dashboard_widget_source_version` FOREIGN KEY (`system_id`, `tenant_id`, `data_source_id`, `data_source_version_id`, `data_source_version_no`) REFERENCES `un_module_data_source_version` (`system_id`, `tenant_id`, `data_source_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_dashboard_widget_version` FOREIGN KEY (`system_id`, `tenant_id`, `dashboard_id`, `dashboard_version_id`, `dashboard_version_no`) REFERENCES `un_module_dashboard_version` (`system_id`, `tenant_id`, `dashboard_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_dashboard_widget_behavior` CHECK ((((`refresh_seconds` = 0) or (`refresh_seconds` between 15 and 3600)) and (`style_variant` in (_utf8mb4'STANDARD',_utf8mb4'COMPACT',_utf8mb4'EMPHASIS')) and ((`click_through` is null) or ((char_length(`click_through`) between 1 and 300) and (`click_through` like _utf8mb4'/%') and (not((`click_through` like _utf8mb4'//%'))) and (not((`click_through` like _utf8mb4'%://%'))))))),
  CONSTRAINT `ck_module_dashboard_widget_code` CHECK (regexp_like(`widget_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_module_dashboard_widget_grid` CHECK (((`grid_x` < 12) and (`grid_width` between 1 and 12) and ((`grid_x` + `grid_width`) <= 12) and (`grid_y` < 100) and (`grid_height` between 1 and 100) and ((`grid_y` + `grid_height`) <= 100))),
  CONSTRAINT `ck_module_dashboard_widget_identity` CHECK (((`id` > 0) and (`dashboard_id` > 0) and (`dashboard_version_id` > 0) and (`dashboard_version_no` > 0) and (`widget_ordinal` < 20))),
  CONSTRAINT `ck_module_dashboard_widget_source_kind` CHECK ((((`widget_type` = _ascii'KPI_VALUE') and (`data_source_id` is null) and (`data_source_version_id` is null) and (`data_source_version_no` is null) and (`data_source_code` is null) and (`module_code` is null) and (`schema_version_id` is null) and (`row_limit` is null) and (`stat_aggregation` is null) and (`stat_measure_field_code` is null) and (`stat_measure_field_id` is null) and (`stat_measure_field_name` is null) and (`stat_measure_field_type` is null) and (`stat_measure_query_type` is null) and (`stat_group_field_code` is null) and (`stat_group_field_id` is null) and (`stat_group_field_name` is null) and (`stat_group_field_type` is null) and (`stat_group_query_type` is null) and (`stat_group_limit` is null) and (`stat_time_field_code` is null) and (`stat_time_field_id` is null) and (`stat_time_field_name` is null) and (`stat_time_field_type` is null) and (`stat_time_query_type` is null) and (`stat_time_grain` is null) and (`stat_time_start` is null) and (`stat_time_end` is null) and (`kpi_id` is not null) and (`kpi_id` > 0) and (`kpi_version_id` is not null) and (`kpi_version_id` > 0) and (`kpi_version_no` is not null) and (`kpi_version_no` > 0) and (`kpi_code` is not null) and (`kpi_name` is not null) and (`kpi_subject_type` is not null) and (`kpi_period_type` is not null) and regexp_like(`kpi_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (char_length(trim(`kpi_name`)) between 1 and 200) and (`kpi_subject_type` in (_ascii'MEMBER',_ascii'DEPARTMENT',_ascii'ROLE')) and (`kpi_period_type` in (_ascii'MONTH',_ascii'QUARTER',_ascii'YEAR'))) or ((`widget_type` <> _ascii'KPI_VALUE') and (`data_source_id` is not null) and (`data_source_id` > 0) and (`data_source_version_id` is not null) and (`data_source_version_id` > 0) and (`data_source_version_no` is not null) and (`data_source_version_no` > 0) and (`data_source_code` is not null) and (`module_code` is not null) and (`schema_version_id` is not null) and regexp_like(`data_source_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`module_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,99}$') and (char_length(trim(`schema_version_id`)) between 1 and 200) and (`kpi_id` is null) and (`kpi_version_id` is null) and (`kpi_version_no` is null) and (`kpi_code` is null) and (`kpi_name` is null) and (`kpi_subject_type` is null) and (`kpi_period_type` is null)))),
  CONSTRAINT `ck_module_dashboard_widget_stat_fields` CHECK ((((`stat_measure_field_code` is null) or regexp_like(`stat_measure_field_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')) and ((`stat_group_field_code` is null) or regexp_like(`stat_group_field_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')) and ((`stat_time_field_code` is null) or regexp_like(`stat_time_field_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')))),
  CONSTRAINT `ck_module_dashboard_widget_stat_metadata` CHECK (((((`stat_measure_field_code` is null) and (`stat_measure_field_id` is null) and (`stat_measure_field_name` is null) and (`stat_measure_field_type` is null) and (`stat_measure_query_type` is null)) or ((`stat_measure_field_code` is not null) and (`stat_measure_field_id` > 0) and (char_length(trim(`stat_measure_field_name`)) between 1 and 200) and regexp_like(`stat_measure_field_type`,_ascii'^[A-Z][A-Z0-9_]{0,99}$') and regexp_like(`stat_measure_query_type`,_ascii'^[A-Z][A-Z0-9_]{0,99}$'))) and (((`stat_group_field_code` is null) and (`stat_group_field_id` is null) and (`stat_group_field_name` is null) and (`stat_group_field_type` is null) and (`stat_group_query_type` is null)) or ((`stat_group_field_code` is not null) and (`stat_group_field_id` > 0) and (char_length(trim(`stat_group_field_name`)) between 1 and 200) and regexp_like(`stat_group_field_type`,_ascii'^[A-Z][A-Z0-9_]{0,99}$') and regexp_like(`stat_group_query_type`,_ascii'^[A-Z][A-Z0-9_]{0,99}$'))) and (((`stat_time_field_code` is null) and (`stat_time_field_id` is null) and (`stat_time_field_name` is null) and (`stat_time_field_type` is null) and (`stat_time_query_type` is null)) or ((`stat_time_field_code` is not null) and (`stat_time_field_id` > 0) and (char_length(trim(`stat_time_field_name`)) between 1 and 200) and regexp_like(`stat_time_field_type`,_ascii'^[A-Z][A-Z0-9_]{0,99}$') and regexp_like(`stat_time_query_type`,_ascii'^[A-Z][A-Z0-9_]{0,99}$'))))),
  CONSTRAINT `ck_module_dashboard_widget_stat_shape` CHECK ((((`stat_aggregation` is null) and (`stat_measure_field_code` is null) and (`stat_group_field_code` is null) and (`stat_group_limit` is null) and (`stat_time_field_code` is null) and (`stat_time_grain` is null) and (`stat_time_start` is null) and (`stat_time_end` is null)) or ((`stat_aggregation` in (_ascii'COUNT',_ascii'SUM',_ascii'AVG',_ascii'MIN',_ascii'MAX')) and (((`stat_aggregation` = _ascii'COUNT') and (`stat_measure_field_code` is null)) or ((`stat_aggregation` <> _ascii'COUNT') and (`stat_measure_field_code` is not null))) and (((`stat_group_field_code` is null) and (`stat_group_limit` is null)) or ((`stat_group_field_code` is not null) and (`stat_group_limit` between 1 and 20))) and (((`stat_time_field_code` is null) and (`stat_time_grain` is null) and (`stat_time_start` is null) and (`stat_time_end` is null)) or ((`stat_time_field_code` is not null) and (`stat_time_grain` in (_ascii'DAY',_ascii'WEEK',_ascii'MONTH')) and (`stat_time_start` is not null) and (`stat_time_end` > `stat_time_start`) and (((`stat_time_grain` = _ascii'DAY') and ((to_days(`stat_time_end`) - to_days(`stat_time_start`)) between 1 and 100)) or ((`stat_time_grain` = _ascii'WEEK') and (weekday(`stat_time_start`) = 0) and (weekday(`stat_time_end`) = 0) and (((to_days(`stat_time_end`) - to_days(`stat_time_start`)) % 7) = 0) and ((to_days(`stat_time_end`) - to_days(`stat_time_start`)) between 7 and 700)) or ((`stat_time_grain` = _ascii'MONTH') and (dayofmonth(`stat_time_start`) = 1) and (dayofmonth(`stat_time_end`) = 1) and (timestampdiff(MONTH,`stat_time_start`,`stat_time_end`) between 1 and 100))))) and ((`stat_group_field_code` is null) or (`stat_time_field_code` is null))))),
  CONSTRAINT `ck_module_dashboard_widget_title` CHECK ((char_length(trim(`widget_title`)) between 1 and 200)),
  CONSTRAINT `ck_module_dashboard_widget_type` CHECK ((((`widget_type` = _utf8mb4'KPI_VALUE') and (`row_limit` is null) and (`stat_aggregation` is null)) or ((`widget_type` = _utf8mb4'STAT_COUNT') and (`row_limit` is null) and (`stat_aggregation` is null)) or ((`widget_type` in (_utf8mb4'DATA_LIST',_utf8mb4'TODO_LIST',_utf8mb4'QUICK_ENTRY')) and (`row_limit` between 1 and 20) and (`stat_aggregation` is null)) or ((`widget_type` in (_utf8mb4'STAT_VALUE',_utf8mb4'PROGRESS')) and (`row_limit` is null) and (`stat_aggregation` is not null) and (`stat_group_field_code` is null) and (`stat_time_field_code` is null)) or ((`widget_type` in (_utf8mb4'BAR_CHART',_utf8mb4'PIE_CHART',_utf8mb4'RANKING')) and (`row_limit` is null) and (`stat_aggregation` is not null) and (`stat_group_field_code` is not null) and (`stat_time_field_code` is null)) or ((`widget_type` = _utf8mb4'LINE_TREND') and (`row_limit` is null) and (`stat_aggregation` is not null) and (`stat_group_field_code` is null) and (`stat_time_field_code` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_dashboard_version_widget`
--

LOCK TABLES `un_module_dashboard_version_widget` WRITE;
/*!40000 ALTER TABLE `un_module_dashboard_version_widget` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_dashboard_version_widget` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_data_source`
--

DROP TABLE IF EXISTS `un_module_data_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_data_source` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `data_source_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_id` bigint NOT NULL,
  `data_source_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `draft_json` json NOT NULL,
  `draft_version` bigint NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `active_version_no` int unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_data_source_code` (`system_id`,`tenant_id`,`data_source_code`),
  UNIQUE KEY `uk_module_data_source_identity` (`system_id`,`tenant_id`,`id`,`active_version_id`,`active_version_no`),
  KEY `idx_module_data_source_list` (`system_id`,`tenant_id`,`updated_at` DESC,`id` DESC),
  KEY `idx_module_data_source_module` (`system_id`,`tenant_id`,`module_id`,`updated_at` DESC,`id` DESC),
  KEY `idx_module_data_source_active` (`system_id`,`tenant_id`,`active_version_id`),
  KEY `fk_module_data_source_module` (`system_id`,`module_id`),
  CONSTRAINT `fk_module_data_source_active_version` FOREIGN KEY (`system_id`, `tenant_id`, `id`, `active_version_id`, `active_version_no`) REFERENCES `un_module_data_source_version` (`system_id`, `tenant_id`, `data_source_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_data_source_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `un_module_definition` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_data_source_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_data_source_active` CHECK ((((`active_version_id` is null) and (`active_version_no` is null)) or ((`active_version_id` > 0) and (`active_version_no` > 0)))),
  CONSTRAINT `ck_module_data_source_code` CHECK (regexp_like(`data_source_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_module_data_source_description` CHECK (((`description` is null) or (char_length(trim(`description`)) between 1 and 2000))),
  CONSTRAINT `ck_module_data_source_draft` CHECK (((`draft_version` > 0) and (json_type(`draft_json`) = _utf8mb4'OBJECT') and (length(`draft_json`) between 2 and 262144))),
  CONSTRAINT `ck_module_data_source_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`module_id` > 0))),
  CONSTRAINT `ck_module_data_source_join_plan` CHECK (((coalesce(json_unquote(json_extract(`draft_json`,_utf8mb4'$.sourceKind')),_utf8mb4'NATIVE_MODULE') <> _utf8mb4'MULTI_MODULE_JOIN') or ((json_type(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin')) = _utf8mb4'OBJECT') and (json_type(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.inputs')) = _utf8mb4'ARRAY') and (json_length(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.inputs')) between 2 and 8) and (json_type(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.edges')) = _utf8mb4'ARRAY') and (json_length(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.edges')) = (json_length(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.inputs')) - 1)) and (json_type(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.projections')) = _utf8mb4'ARRAY') and (json_length(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.projections')) between 1 and 50) and (cast(json_unquote(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.timeoutSeconds')) as unsigned) between 1 and 10) and (cast(json_unquote(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.rowLimit')) as unsigned) between 1 and 100) and (json_unquote(json_extract(`draft_json`,_utf8mb4'$.multiModuleJoin.failureMode')) in (_utf8mb4'FAIL_FAST',_utf8mb4'ALLOW_PARTIAL_LEFT'))))),
  CONSTRAINT `ck_module_data_source_name` CHECK ((char_length(trim(`data_source_name`)) between 1 and 200)),
  CONSTRAINT `ck_module_data_source_state` CHECK (((`updated_at` >= `created_at`) and (`version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_data_source`
--

LOCK TABLES `un_module_data_source` WRITE;
/*!40000 ALTER TABLE `un_module_data_source` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_data_source` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_data_source_version`
--

DROP TABLE IF EXISTS `un_module_data_source_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_data_source_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `data_source_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `source_draft_version` bigint NOT NULL,
  `data_source_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_id` bigint NOT NULL,
  `module_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schema_version_id` varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `data_source_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `snapshot_json` json NOT NULL,
  `snapshot_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `published_by_member_id` bigint NOT NULL,
  `published_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`data_source_id`,`id`),
  UNIQUE KEY `uk_module_data_source_version_id` (`system_id`,`tenant_id`,`data_source_id`,`id`,`version_no`),
  UNIQUE KEY `uk_module_data_source_version_no` (`system_id`,`tenant_id`,`data_source_id`,`version_no`),
  KEY `idx_module_data_source_version_list` (`system_id`,`tenant_id`,`data_source_id`,`version_no` DESC),
  KEY `idx_module_data_source_version_fingerprint` (`system_id`,`tenant_id`,`data_source_id`,`snapshot_fingerprint`,`version_no` DESC),
  KEY `fk_module_data_source_version_module` (`system_id`,`module_id`),
  CONSTRAINT `fk_module_data_source_version_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `un_module_definition` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_data_source_version_root` FOREIGN KEY (`system_id`, `tenant_id`, `data_source_id`) REFERENCES `un_module_data_source` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_data_source_version_code` CHECK ((regexp_like(`data_source_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`module_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,99}$'))),
  CONSTRAINT `ck_module_data_source_version_identity` CHECK (((`id` > 0) and (`data_source_id` > 0) and (`version_no` > 0) and (`source_draft_version` > 0) and (`module_id` > 0) and (`published_by_member_id` > 0))),
  CONSTRAINT `ck_module_data_source_version_join_plan` CHECK (((coalesce(json_unquote(json_extract(`snapshot_json`,_utf8mb4'$.sourceKind')),_utf8mb4'NATIVE_MODULE') <> _utf8mb4'MULTI_MODULE_JOIN') or ((json_type(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin')) = _utf8mb4'OBJECT') and (json_length(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin.inputs')) between 2 and 8) and (json_length(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin.edges')) = (json_length(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin.inputs')) - 1)) and (json_length(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin.projections')) between 1 and 50) and (cast(json_unquote(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin.timeoutSeconds')) as unsigned) between 1 and 10) and (cast(json_unquote(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin.rowLimit')) as unsigned) between 1 and 100) and (json_unquote(json_extract(`snapshot_json`,_utf8mb4'$.multiModuleJoin.failureMode')) in (_utf8mb4'FAIL_FAST',_utf8mb4'ALLOW_PARTIAL_LEFT'))))),
  CONSTRAINT `ck_module_data_source_version_name` CHECK (((char_length(trim(`data_source_name`)) between 1 and 200) and ((`description` is null) or (char_length(trim(`description`)) between 1 and 2000)))),
  CONSTRAINT `ck_module_data_source_version_schema` CHECK ((char_length(trim(`schema_version_id`)) between 1 and 200)),
  CONSTRAINT `ck_module_data_source_version_snapshot` CHECK (((json_type(`snapshot_json`) = _utf8mb4'OBJECT') and (length(`snapshot_json`) between 2 and 262144) and regexp_like(`snapshot_fingerprint`,_ascii'^[0-9a-f]{64}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_data_source_version`
--

LOCK TABLES `un_module_data_source_version` WRITE;
/*!40000 ALTER TABLE `un_module_data_source_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_data_source_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_definition`
--

DROP TABLE IF EXISTS `un_module_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_definition` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `module_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `icon_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `allow_comments` tinyint(1) NOT NULL DEFAULT '0',
  `allow_team` tinyint(1) NOT NULL DEFAULT '0',
  `code_locked_at` datetime(3) DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_definition_system_code` (`system_id`,`module_code`),
  UNIQUE KEY `uk_module_definition_system_id` (`system_id`,`id`),
  KEY `idx_module_definition_group` (`system_id`,`group_id`,`desired_status`,`sort_order`,`id`),
  CONSTRAINT `fk_module_definition_group` FOREIGN KEY (`system_id`, `group_id`) REFERENCES `un_module_group` (`system_id`, `id`),
  CONSTRAINT `ck_module_definition_code` CHECK (regexp_like(`module_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_definition_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_definition_flags` CHECK (((`allow_comments` in (0,1)) and (`allow_team` in (0,1)))),
  CONSTRAINT `ck_module_definition_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_definition_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_definition`
--

LOCK TABLES `un_module_definition` WRITE;
/*!40000 ALTER TABLE `un_module_definition` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_dictionary`
--

DROP TABLE IF EXISTS `un_module_dictionary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_dictionary` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `dictionary_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dictionary_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dictionary_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `code_locked_at` datetime(3) DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_dictionary_system_code` (`system_id`,`dictionary_code`),
  UNIQUE KEY `uk_module_dictionary_system_id` (`system_id`,`id`),
  KEY `idx_module_dictionary_list` (`system_id`,`desired_status`,`category`,`dictionary_name`),
  CONSTRAINT `fk_module_dictionary_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_module_dictionary_code` CHECK (regexp_like(`dictionary_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_dictionary_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_dictionary_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_dictionary_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_module_dictionary_type` CHECK ((`dictionary_type` in (_utf8mb4'LIST',_utf8mb4'TREE',_utf8mb4'CASCADE',_utf8mb4'STATUS',_utf8mb4'TAG',_utf8mb4'FIELD_OPTION')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_dictionary`
--

LOCK TABLES `un_module_dictionary` WRITE;
/*!40000 ALTER TABLE `un_module_dictionary` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_dictionary` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_dictionary_item`
--

DROP TABLE IF EXISTS `un_module_dictionary_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_dictionary_item` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `dictionary_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `semantic_key` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color_value` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `icon_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `depth_level` int NOT NULL DEFAULT '0',
  `depth_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `code_locked_at` datetime(3) DEFAULT NULL,
  `active_marker` bigint GENERATED ALWAYS AS ((case when ((`is_default` = 1) and (`desired_status` = _utf8mb4'ENABLED') and (`deleted_at` is null)) then `dictionary_id` else NULL end)) STORED,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_dict_item_code` (`system_id`,`dictionary_id`,`item_code`),
  UNIQUE KEY `uk_module_dict_item_scope_id` (`system_id`,`dictionary_id`,`id`),
  UNIQUE KEY `uk_module_dict_item_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_module_dict_item_default` (`active_marker`),
  KEY `idx_module_dict_item_parent` (`system_id`,`dictionary_id`,`parent_id`,`sort_order`,`id`),
  KEY `idx_module_dict_item_list` (`system_id`,`dictionary_id`,`desired_status`,`sort_order`,`id`),
  CONSTRAINT `fk_module_dict_item_dictionary` FOREIGN KEY (`system_id`, `dictionary_id`) REFERENCES `un_module_dictionary` (`system_id`, `id`),
  CONSTRAINT `fk_module_dict_item_parent` FOREIGN KEY (`system_id`, `dictionary_id`, `parent_id`) REFERENCES `un_module_dictionary_item` (`system_id`, `dictionary_id`, `id`),
  CONSTRAINT `ck_module_dict_item_code` CHECK (regexp_like(`item_code`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$')),
  CONSTRAINT `ck_module_dict_item_default` CHECK ((`is_default` in (0,1))),
  CONSTRAINT `ck_module_dict_item_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_dict_item_depth` CHECK (((`depth_level` >= 0) and (`depth_level` <= 16))),
  CONSTRAINT `ck_module_dict_item_parent_self` CHECK (((`parent_id` is null) or (`parent_id` <> `id`))),
  CONSTRAINT `ck_module_dict_item_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_dict_item_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_dictionary_item`
--

LOCK TABLES `un_module_dictionary_item` WRITE;
/*!40000 ALTER TABLE `un_module_dictionary_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_dictionary_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_dictionary_item_closure`
--

DROP TABLE IF EXISTS `un_module_dictionary_item_closure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_dictionary_item_closure` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `dictionary_id` bigint NOT NULL,
  `ancestor_id` bigint NOT NULL,
  `descendant_id` bigint NOT NULL,
  `depth` int NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_dict_closure_path` (`system_id`,`dictionary_id`,`ancestor_id`,`descendant_id`),
  KEY `idx_module_dict_closure_desc` (`system_id`,`dictionary_id`,`descendant_id`,`depth`,`ancestor_id`),
  CONSTRAINT `fk_module_dict_closure_ancestor` FOREIGN KEY (`system_id`, `dictionary_id`, `ancestor_id`) REFERENCES `un_module_dictionary_item` (`system_id`, `dictionary_id`, `id`),
  CONSTRAINT `fk_module_dict_closure_descendant` FOREIGN KEY (`system_id`, `dictionary_id`, `descendant_id`) REFERENCES `un_module_dictionary_item` (`system_id`, `dictionary_id`, `id`),
  CONSTRAINT `ck_module_dict_closure_depth` CHECK (((`depth` >= 0) and (((`ancestor_id` = `descendant_id`) and (`depth` = 0)) or ((`ancestor_id` <> `descendant_id`) and (`depth` > 0)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_dictionary_item_closure`
--

LOCK TABLES `un_module_dictionary_item_closure` WRITE;
/*!40000 ALTER TABLE `un_module_dictionary_item_closure` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_dictionary_item_closure` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_export_task`
--

DROP TABLE IF EXISTS `un_module_export_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_export_task` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `schema_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `query_json` json NOT NULL,
  `query_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_codes_json` json NOT NULL,
  `permission_snapshot_json` json NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_rows` int DEFAULT NULL,
  `processed_rows` int NOT NULL DEFAULT '0',
  `result_filename` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result_content` longblob,
  `result_size` bigint DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `job_id` bigint NOT NULL,
  `requested_by_account_id` bigint NOT NULL,
  `requested_by_member_id` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_export_job` (`job_id`),
  KEY `idx_module_export_owner` (`system_id`,`tenant_id`,`logical_module_id`,`requested_by_member_id`,`created_at`),
  KEY `idx_module_export_status` (`status`,`updated_at`),
  CONSTRAINT `ck_module_export_counts` CHECK ((((`total_rows` is null) or (`total_rows` between 0 and 5000)) and (`processed_rows` between 0 and 5000))),
  CONSTRAINT `ck_module_export_status` CHECK ((`status` in (_utf8mb4'QUEUED',_utf8mb4'RUNNING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_export_task`
--

LOCK TABLES `un_module_export_task` WRITE;
/*!40000 ALTER TABLE `un_module_export_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_export_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_favorite`
--

DROP TABLE IF EXISTS `un_module_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_favorite` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `record_id` bigint DEFAULT NULL,
  `record_schema_version_id` bigint DEFAULT NULL,
  `record_module_snapshot_id` bigint DEFAULT NULL,
  `target_record_key` bigint GENERATED ALWAYS AS (coalesce(`record_id`,0)) STORED,
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`deleted_at` is null) then 1 else NULL end)) STORED,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_target` (`system_id`,`tenant_id`,`member_id`,`target_type`,`logical_module_id`,`target_record_key`,`active_marker`),
  KEY `idx_favorite_member` (`system_id`,`tenant_id`,`member_id`,`updated_at`,`id`),
  KEY `fk_favorite_member` (`system_id`,`member_id`),
  KEY `fk_favorite_module` (`system_id`,`module_schema_version_id`,`module_snapshot_id`),
  KEY `fk_favorite_record` (`system_id`,`tenant_id`,`record_id`,`record_schema_version_id`,`record_module_snapshot_id`),
  CONSTRAINT `fk_favorite_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_favorite_module` FOREIGN KEY (`system_id`, `module_schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_runtime_schema_module` (`system_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_favorite_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `record_schema_version_id`, `record_module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_favorite_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_favorite_delete` CHECK ((((`deleted_at` is null) and (`deleted_by` is null)) or ((`deleted_at` is not null) and (`deleted_by` is not null)))),
  CONSTRAINT `ck_favorite_target` CHECK ((((`target_type` = _utf8mb4'MODULE') and (`record_id` is null) and (`record_schema_version_id` is null) and (`record_module_snapshot_id` is null)) or ((`target_type` = _utf8mb4'RECORD') and (`record_id` is not null) and (`record_id` > 0) and (`record_schema_version_id` is not null) and (`record_module_snapshot_id` is not null)))),
  CONSTRAINT `ck_favorite_target_type` CHECK ((`target_type` in (_utf8mb4'MODULE',_utf8mb4'RECORD'))),
  CONSTRAINT `ck_favorite_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_favorite`
--

LOCK TABLES `un_module_favorite` WRITE;
/*!40000 ALTER TABLE `un_module_favorite` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_favorite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_field`
--

DROP TABLE IF EXISTS `un_module_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_field` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `module_id` bigint NOT NULL,
  `dictionary_id` bigint DEFAULT NULL,
  `target_module_id` bigint DEFAULT NULL,
  `field_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `is_required` tinyint(1) NOT NULL DEFAULT '0',
  `is_hidden` tinyint(1) NOT NULL DEFAULT '0',
  `is_readonly` tinyint(1) NOT NULL DEFAULT '0',
  `is_searchable` tinyint(1) NOT NULL DEFAULT '0',
  `is_filterable` tinyint(1) NOT NULL DEFAULT '0',
  `show_in_list` tinyint(1) NOT NULL DEFAULT '1',
  `show_in_detail` tinyint(1) NOT NULL DEFAULT '1',
  `index_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `property_json` json NOT NULL,
  `code_locked_at` datetime(3) DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_field_module_code` (`system_id`,`module_id`,`field_code`),
  UNIQUE KEY `uk_module_field_system_id` (`system_id`,`id`),
  KEY `idx_module_field_list` (`system_id`,`module_id`,`desired_status`,`sort_order`,`id`),
  KEY `idx_module_field_dictionary` (`system_id`,`dictionary_id`),
  KEY `idx_module_field_target` (`system_id`,`target_module_id`),
  CONSTRAINT `fk_module_field_dictionary` FOREIGN KEY (`system_id`, `dictionary_id`) REFERENCES `un_module_dictionary` (`system_id`, `id`),
  CONSTRAINT `fk_module_field_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `fk_module_field_target` FOREIGN KEY (`system_id`, `target_module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `ck_module_field_code` CHECK (regexp_like(`field_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_field_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_field_flags` CHECK (((`is_required` in (0,1)) and (`is_hidden` in (0,1)) and (`is_readonly` in (0,1)) and (`is_searchable` in (0,1)) and (`is_filterable` in (0,1)) and (`show_in_list` in (0,1)) and (`show_in_detail` in (0,1)))),
  CONSTRAINT `ck_module_field_index` CHECK ((`index_mode` in (_utf8mb4'NONE',_utf8mb4'FILTER',_utf8mb4'SORT',_utf8mb4'UNIQUE',_utf8mb4'STATISTIC'))),
  CONSTRAINT `ck_module_field_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_field_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_module_field_type` CHECK ((`field_type` in (_utf8mb4'TEXT',_utf8mb4'TEXTAREA',_utf8mb4'PHONE',_utf8mb4'EMAIL',_utf8mb4'URL',_utf8mb4'IDENTITY',_utf8mb4'NUMBER',_utf8mb4'PERCENT',_utf8mb4'MONEY',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'DATE_RANGE',_utf8mb4'TIME',_utf8mb4'TIME_RANGE',_utf8mb4'RADIO',_utf8mb4'MULTI_SELECT',_utf8mb4'CASCADE',_utf8mb4'SWITCH',_utf8mb4'MEMBER',_utf8mb4'DEPARTMENT',_utf8mb4'TENANT',_utf8mb4'ATTACHMENT',_utf8mb4'IMAGE',_utf8mb4'FILE_GROUP',_utf8mb4'AUTO_NUMBER',_utf8mb4'RELATION',_utf8mb4'REFERENCE',_utf8mb4'SUBTABLE',_utf8mb4'ADDRESS',_utf8mb4'GEO',_utf8mb4'RATING',_utf8mb4'PROGRESS',_utf8mb4'TAG',_utf8mb4'BARCODE',_utf8mb4'SIGNATURE',_utf8mb4'RICH_TEXT',_utf8mb4'JSON',_utf8mb4'SECRET',_utf8mb4'STATUS',_utf8mb4'FORMULA',_utf8mb4'SUMMARY',_utf8mb4'CALCULATED',_utf8mb4'LOOKUP',_utf8mb4'AGGREGATE',_utf8mb4'AI_FILL',_utf8mb4'CREATED_BY',_utf8mb4'CREATED_AT',_utf8mb4'UPDATED_BY',_utf8mb4'UPDATED_AT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_field`
--

LOCK TABLES `un_module_field` WRITE;
/*!40000 ALTER TABLE `un_module_field` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_field` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_group`
--

DROP TABLE IF EXISTS `un_module_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_group` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `group_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `group_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `icon_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `code_locked_at` datetime(3) DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_group_system_code` (`system_id`,`group_code`),
  UNIQUE KEY `uk_module_group_system_id` (`system_id`,`id`),
  KEY `idx_module_group_list` (`system_id`,`desired_status`,`sort_order`,`id`),
  CONSTRAINT `fk_module_group_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_module_group_code` CHECK (regexp_like(`group_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_group_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_group_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_group_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_group`
--

LOCK TABLES `un_module_group` WRITE;
/*!40000 ALTER TABLE `un_module_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_import_batch`
--

DROP TABLE IF EXISTS `un_module_import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_import_batch` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `schema_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `import_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `match_field_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_rows` int NOT NULL,
  `new_rows` int NOT NULL DEFAULT '0',
  `update_rows` int NOT NULL DEFAULT '0',
  `failed_rows` int NOT NULL DEFAULT '0',
  `preview_job_id` bigint NOT NULL,
  `commit_job_id` bigint DEFAULT NULL,
  `rollback_job_id` bigint DEFAULT NULL,
  `requested_by_account_id` bigint NOT NULL,
  `requested_by_member_id` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `committed_at` datetime(3) DEFAULT NULL,
  `rolled_back_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_import_preview_job` (`preview_job_id`),
  UNIQUE KEY `uk_module_import_commit_job` (`commit_job_id`),
  UNIQUE KEY `uk_module_import_rollback_job` (`rollback_job_id`),
  KEY `idx_module_import_owner` (`system_id`,`tenant_id`,`logical_module_id`,`requested_by_member_id`,`created_at`),
  KEY `idx_module_import_status` (`status`,`updated_at`),
  CONSTRAINT `ck_module_import_counts` CHECK (((`total_rows` between 1 and 200) and (`new_rows` >= 0) and (`update_rows` >= 0) and (`failed_rows` >= 0))),
  CONSTRAINT `ck_module_import_mode` CHECK ((`import_mode` in (_utf8mb4'NEW',_utf8mb4'UPSERT'))),
  CONSTRAINT `ck_module_import_status` CHECK ((`status` in (_utf8mb4'PREVIEW_QUEUED',_utf8mb4'PREVIEWING',_utf8mb4'READY',_utf8mb4'INVALID',_utf8mb4'COMMIT_QUEUED',_utf8mb4'COMMITTING',_utf8mb4'COMMITTED',_utf8mb4'ROLLBACK_QUEUED',_utf8mb4'ROLLING_BACK',_utf8mb4'ROLLED_BACK',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_import_batch`
--

LOCK TABLES `un_module_import_batch` WRITE;
/*!40000 ALTER TABLE `un_module_import_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_import_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_import_row`
--

DROP TABLE IF EXISTS `un_module_import_row`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_import_row` (
  `id` bigint NOT NULL,
  `batch_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_row_number` int NOT NULL,
  `planned_action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `row_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_json` json NOT NULL,
  `unique_fingerprint_json` json DEFAULT NULL,
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_record_id` bigint DEFAULT NULL,
  `target_before_json` json DEFAULT NULL,
  `target_after_version` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_import_row` (`batch_id`,`source_row_number`),
  KEY `idx_module_import_row_status` (`batch_id`,`row_status`,`source_row_number`),
  KEY `idx_module_import_row_target` (`system_id`,`tenant_id`,`target_record_id`),
  CONSTRAINT `fk_module_import_row_batch` FOREIGN KEY (`batch_id`) REFERENCES `un_module_import_batch` (`id`),
  CONSTRAINT `ck_module_import_row_action` CHECK (((`planned_action` is null) or (`planned_action` in (_utf8mb4'NEW',_utf8mb4'UPDATE')))),
  CONSTRAINT `ck_module_import_row_status` CHECK ((`row_status` in (_utf8mb4'PREVIEW_PENDING',_utf8mb4'VALID',_utf8mb4'INVALID',_utf8mb4'COMMITTED',_utf8mb4'ROLLED_BACK')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_import_row`
--

LOCK TABLES `un_module_import_row` WRITE;
/*!40000 ALTER TABLE `un_module_import_row` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_import_row` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_kpi`
--

DROP TABLE IF EXISTS `un_module_kpi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_kpi` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `kpi_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kpi_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `draft_json` json NOT NULL,
  `draft_version` bigint NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `active_version_no` int unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_kpi_code` (`system_id`,`tenant_id`,`kpi_code`),
  UNIQUE KEY `uk_module_kpi_active_identity` (`system_id`,`tenant_id`,`id`,`active_version_id`,`active_version_no`),
  KEY `idx_module_kpi_list` (`system_id`,`tenant_id`,`updated_at` DESC,`id` DESC),
  KEY `idx_module_kpi_active` (`system_id`,`tenant_id`,`active_version_id`),
  CONSTRAINT `fk_module_kpi_active_version` FOREIGN KEY (`system_id`, `tenant_id`, `id`, `active_version_id`, `active_version_no`) REFERENCES `un_module_kpi_version` (`system_id`, `tenant_id`, `kpi_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_kpi_active` CHECK ((((`active_version_id` is null) and (`active_version_no` is null)) or ((`active_version_id` > 0) and (`active_version_no` > 0)))),
  CONSTRAINT `ck_module_kpi_code` CHECK (regexp_like(`kpi_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_module_kpi_description` CHECK (((`description` is null) or (char_length(trim(`description`)) between 1 and 2000))),
  CONSTRAINT `ck_module_kpi_draft` CHECK (((`draft_version` > 0) and (json_type(`draft_json`) = _utf8mb4'OBJECT') and (length(`draft_json`) between 2 and 262144))),
  CONSTRAINT `ck_module_kpi_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0))),
  CONSTRAINT `ck_module_kpi_name` CHECK ((char_length(trim(`kpi_name`)) between 1 and 200)),
  CONSTRAINT `ck_module_kpi_state` CHECK (((`updated_at` >= `created_at`) and (`version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_kpi`
--

LOCK TABLES `un_module_kpi` WRITE;
/*!40000 ALTER TABLE `un_module_kpi` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_kpi` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_kpi_calculation`
--

DROP TABLE IF EXISTS `un_module_kpi_calculation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_kpi_calculation` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `kpi_id` bigint NOT NULL,
  `kpi_version_id` bigint NOT NULL,
  `kpi_version_no` int unsigned NOT NULL,
  `target_id` bigint NOT NULL,
  `calculation_no` int unsigned NOT NULL,
  `request_key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `running_target_id` bigint GENERATED ALWAYS AS ((case when (`status` = _ascii'RUNNING') then `target_id` else NULL end)) STORED,
  `calculator_version` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `authz_epoch` bigint DEFAULT NULL,
  `subject_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` bigint NOT NULL,
  `subject_name` varchar(200) NOT NULL,
  `subject_member_count` int unsigned NOT NULL,
  `subject_members_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `subject_snapshot_json` json NOT NULL,
  `period_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `aggregation` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_value_snapshot` varchar(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actual_value` varchar(1000) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `attainment_rate` varchar(1000) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `warning_status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `statistics_query_id` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `matched_record_count` bigint unsigned DEFAULT NULL,
  `trend_json` json DEFAULT NULL,
  `explanation_json` json DEFAULT NULL,
  `error_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `requested_by_member_id` bigint NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_kpi_calculation_identity` (`system_id`,`tenant_id`,`target_id`,`id`,`calculation_no`),
  UNIQUE KEY `uk_module_kpi_calculation_member_identity` (`system_id`,`tenant_id`,`kpi_id`,`target_id`,`id`),
  UNIQUE KEY `uk_module_kpi_calculation_no` (`system_id`,`tenant_id`,`target_id`,`calculation_no`),
  UNIQUE KEY `uk_module_kpi_calculation_request` (`system_id`,`tenant_id`,`request_key_hash`),
  UNIQUE KEY `uk_module_kpi_calculation_running` (`system_id`,`tenant_id`,`running_target_id`),
  KEY `idx_module_kpi_calculation_history` (`system_id`,`tenant_id`,`target_id`,`calculation_no` DESC),
  KEY `idx_module_kpi_calculation_status` (`system_id`,`tenant_id`,`status`,`started_at`,`id`),
  KEY `fk_module_kpi_calculation_target` (`system_id`,`tenant_id`,`kpi_id`,`kpi_version_id`,`kpi_version_no`,`target_id`),
  KEY `fk_module_kpi_calculation_requester` (`system_id`,`requested_by_member_id`),
  CONSTRAINT `fk_module_kpi_calculation_requester` FOREIGN KEY (`system_id`, `requested_by_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_calculation_target` FOREIGN KEY (`system_id`, `tenant_id`, `kpi_id`, `kpi_version_id`, `kpi_version_no`, `target_id`) REFERENCES `un_module_kpi_target` (`system_id`, `tenant_id`, `kpi_id`, `kpi_version_id`, `kpi_version_no`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_kpi_calculation_identity` CHECK (((`id` > 0) and (`kpi_id` > 0) and (`kpi_version_id` > 0) and (`kpi_version_no` > 0) and (`target_id` > 0) and (`calculation_no` > 0) and (`requested_by_member_id` > 0))),
  CONSTRAINT `ck_module_kpi_calculation_json` CHECK ((((`trend_json` is null) or (length(`trend_json`) <= 131072)) and ((`explanation_json` is null) or (length(`explanation_json`) between 2 and 262144)))),
  CONSTRAINT `ck_module_kpi_calculation_period` CHECK (((`period_type` in (_utf8mb4'MONTH',_utf8mb4'QUARTER',_utf8mb4'YEAR')) and (`period_end` > `period_start`))),
  CONSTRAINT `ck_module_kpi_calculation_request` CHECK ((regexp_like(`request_key_hash`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`request_fingerprint`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`calculator_version`,_utf8mb4'^[A-Za-z][A-Za-z0-9_.-]{0,31}$'))),
  CONSTRAINT `ck_module_kpi_calculation_result` CHECK ((((`status` = _utf8mb4'RUNNING') and (`running_target_id` = `target_id`) and (`authz_epoch` > 0) and (`completed_at` is null) and (`actual_value` is null) and (`attainment_rate` is null) and (`warning_status` is null) and (`statistics_query_id` is null) and (`matched_record_count` is null) and (`trend_json` is null) and (`explanation_json` is null) and (`error_code` is null) and (`error_message` is null)) or ((`status` = _utf8mb4'SUCCEEDED') and (`running_target_id` is null) and (`completed_at` >= `started_at`) and (`authz_epoch` > 0) and (`warning_status` in (_utf8mb4'ACHIEVED',_utf8mb4'AT_RISK',_utf8mb4'MISSED')) and (((`actual_value` is null) and (`attainment_rate` is null)) or ((`actual_value` is not null) and (`attainment_rate` is not null))) and regexp_like(`statistics_query_id`,_utf8mb4'^[0-9a-f]{64}$') and (`matched_record_count` >= 0) and (json_type(`trend_json`) = _utf8mb4'ARRAY') and (json_type(`explanation_json`) = _utf8mb4'OBJECT') and (`error_code` is null) and (`error_message` is null)) or ((`status` = _utf8mb4'FAILED') and (`running_target_id` is null) and (`completed_at` >= `started_at`) and ((`authz_epoch` is null) or (`authz_epoch` > 0)) and (`actual_value` is null) and (`attainment_rate` is null) and (`warning_status` = _utf8mb4'CALCULATION_FAILED') and (`statistics_query_id` is null) and (`matched_record_count` is null) and (`trend_json` is null) and regexp_like(`error_code`,_utf8mb4'^[A-Z][A-Z0-9_]{0,99}$') and (char_length(trim(`error_message`)) between 1 and 500)))),
  CONSTRAINT `ck_module_kpi_calculation_subject` CHECK (((`subject_type` in (_utf8mb4'MEMBER',_utf8mb4'DEPARTMENT',_utf8mb4'ROLE')) and (`subject_id` > 0) and (char_length(trim(`subject_name`)) between 1 and 200) and (`subject_member_count` <= 1000) and (json_type(`subject_snapshot_json`) = _utf8mb4'OBJECT') and (length(`subject_snapshot_json`) between 2 and 131072) and (((`subject_type` = _utf8mb4'ROLE') and regexp_like(`subject_members_fingerprint`,_utf8mb4'^[0-9a-f]{64}$')) or ((`subject_type` <> _utf8mb4'ROLE') and (`subject_members_fingerprint` is null))))),
  CONSTRAINT `ck_module_kpi_calculation_values` CHECK (((`aggregation` in (_utf8mb4'COUNT',_utf8mb4'SUM',_utf8mb4'AVG',_utf8mb4'MIN',_utf8mb4'MAX')) and regexp_like(`target_value_snapshot`,_utf8mb4'^(0|[1-9][0-9]*)([.][0-9]*[1-9])?$') and ((`actual_value` is null) or regexp_like(`actual_value`,_utf8mb4'^(0|-?[1-9][0-9]*)([.][0-9]*[1-9])?$')) and ((`attainment_rate` is null) or regexp_like(`attainment_rate`,_utf8mb4'^(0|[1-9][0-9]*)([.][0-9]*[1-9])?$'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_kpi_calculation`
--

LOCK TABLES `un_module_kpi_calculation` WRITE;
/*!40000 ALTER TABLE `un_module_kpi_calculation` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_kpi_calculation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_kpi_calculation_member`
--

DROP TABLE IF EXISTS `un_module_kpi_calculation_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_kpi_calculation_member` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `kpi_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  `calculation_id` bigint NOT NULL,
  `member_ordinal` smallint unsigned NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`kpi_id`,`target_id`,`calculation_id`,`member_ordinal`),
  UNIQUE KEY `uk_module_kpi_calculation_member` (`system_id`,`tenant_id`,`kpi_id`,`target_id`,`calculation_id`,`member_id`),
  KEY `idx_module_kpi_calculation_member_lookup` (`system_id`,`tenant_id`,`member_id`,`calculation_id`),
  KEY `fk_module_kpi_calculation_member_member` (`system_id`,`member_id`),
  CONSTRAINT `fk_module_kpi_calculation_member_calc` FOREIGN KEY (`system_id`, `tenant_id`, `kpi_id`, `target_id`, `calculation_id`) REFERENCES `un_module_kpi_calculation` (`system_id`, `tenant_id`, `kpi_id`, `target_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_calculation_member_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_kpi_calculation_member_identity` CHECK (((`kpi_id` > 0) and (`target_id` > 0) and (`calculation_id` > 0) and (`member_ordinal` < 1000) and (`member_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_kpi_calculation_member`
--

LOCK TABLES `un_module_kpi_calculation_member` WRITE;
/*!40000 ALTER TABLE `un_module_kpi_calculation_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_kpi_calculation_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_kpi_target`
--

DROP TABLE IF EXISTS `un_module_kpi_target`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_kpi_target` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `kpi_id` bigint NOT NULL,
  `kpi_version_id` bigint NOT NULL,
  `kpi_version_no` int unsigned NOT NULL,
  `subject_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` bigint NOT NULL,
  `subject_name` varchar(200) NOT NULL,
  `period_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `target_value` varchar(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `latest_calculation_id` bigint DEFAULT NULL,
  `latest_calculation_no` int unsigned DEFAULT NULL,
  `created_by_member_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_by_member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_kpi_target_version_identity` (`system_id`,`tenant_id`,`kpi_id`,`kpi_version_id`,`kpi_version_no`,`id`),
  UNIQUE KEY `uk_module_kpi_target_period` (`system_id`,`tenant_id`,`kpi_id`,`kpi_version_id`,`subject_type`,`subject_id`,`period_start`),
  UNIQUE KEY `uk_module_kpi_target_request` (`system_id`,`tenant_id`,`kpi_id`,`request_key_hash`),
  UNIQUE KEY `uk_module_kpi_target_latest_identity` (`system_id`,`tenant_id`,`id`,`latest_calculation_id`,`latest_calculation_no`),
  KEY `idx_module_kpi_target_list` (`system_id`,`tenant_id`,`kpi_id`,`period_start` DESC,`id` DESC),
  KEY `idx_module_kpi_target_subject` (`system_id`,`tenant_id`,`subject_type`,`subject_id`,`period_start` DESC,`id` DESC),
  KEY `idx_module_kpi_target_latest` (`system_id`,`tenant_id`,`latest_calculation_id`),
  KEY `fk_module_kpi_target_creator` (`system_id`,`created_by_member_id`),
  KEY `fk_module_kpi_target_updater` (`system_id`,`updated_by_member_id`),
  CONSTRAINT `fk_module_kpi_target_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_target_latest` FOREIGN KEY (`system_id`, `tenant_id`, `id`, `latest_calculation_id`, `latest_calculation_no`) REFERENCES `un_module_kpi_calculation` (`system_id`, `tenant_id`, `target_id`, `id`, `calculation_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_target_updater` FOREIGN KEY (`system_id`, `updated_by_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_target_version` FOREIGN KEY (`system_id`, `tenant_id`, `kpi_id`, `kpi_version_id`, `kpi_version_no`) REFERENCES `un_module_kpi_version` (`system_id`, `tenant_id`, `kpi_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_kpi_target_identity` CHECK (((`id` > 0) and (`kpi_id` > 0) and (`kpi_version_id` > 0) and (`kpi_version_no` > 0) and (`subject_id` > 0) and (`created_by_member_id` > 0) and (`updated_by_member_id` > 0))),
  CONSTRAINT `ck_module_kpi_target_period` CHECK ((((`period_type` = _ascii'MONTH') and (dayofmonth(`period_start`) = 1) and (`period_end` = (`period_start` + interval 1 month))) or ((`period_type` = _ascii'QUARTER') and (dayofmonth(`period_start`) = 1) and (month(`period_start`) in (1,4,7,10)) and (`period_end` = (`period_start` + interval 3 month))) or ((`period_type` = _ascii'YEAR') and (month(`period_start`) = 1) and (dayofmonth(`period_start`) = 1) and (`period_end` = (`period_start` + interval 1 year))))),
  CONSTRAINT `ck_module_kpi_target_request` CHECK ((regexp_like(`request_key_hash`,_ascii'^[0-9a-f]{64}$') and regexp_like(`request_fingerprint`,_ascii'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_module_kpi_target_state` CHECK (((`status` in (_ascii'ACTIVE',_ascii'CANCELLED')) and (((`latest_calculation_id` is null) and (`latest_calculation_no` is null)) or ((`latest_calculation_id` > 0) and (`latest_calculation_no` > 0))) and (`updated_at` >= `created_at`) and (`version` > 0))),
  CONSTRAINT `ck_module_kpi_target_subject` CHECK (((`subject_type` in (_ascii'MEMBER',_ascii'DEPARTMENT',_ascii'ROLE')) and (char_length(trim(`subject_name`)) between 1 and 200))),
  CONSTRAINT `ck_module_kpi_target_value` CHECK (regexp_like(`target_value`,_ascii'^(0|[1-9][0-9]*)([.][0-9]*[1-9])?$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_kpi_target`
--

LOCK TABLES `un_module_kpi_target` WRITE;
/*!40000 ALTER TABLE `un_module_kpi_target` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_kpi_target` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_kpi_version`
--

DROP TABLE IF EXISTS `un_module_kpi_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_kpi_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `kpi_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `source_draft_version` bigint NOT NULL,
  `kpi_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kpi_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `subject_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `period_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attainment_direction` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `warning_threshold` varchar(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `data_source_id` bigint NOT NULL,
  `data_source_version_id` bigint NOT NULL,
  `data_source_version_no` int unsigned NOT NULL,
  `data_source_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_id` bigint NOT NULL,
  `module_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schema_version_id` varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `aggregation` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `measure_field_id` bigint DEFAULT NULL,
  `measure_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `measure_field_name` varchar(200) DEFAULT NULL,
  `measure_field_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `measure_query_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `time_field_id` bigint NOT NULL,
  `time_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `time_field_name` varchar(200) NOT NULL,
  `time_field_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `time_query_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `calculator_version` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `snapshot_json` json NOT NULL,
  `snapshot_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `published_by_member_id` bigint NOT NULL,
  `published_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`kpi_id`,`id`),
  UNIQUE KEY `uk_module_kpi_version_id` (`system_id`,`tenant_id`,`kpi_id`,`id`,`version_no`),
  UNIQUE KEY `uk_module_kpi_version_no` (`system_id`,`tenant_id`,`kpi_id`,`version_no`),
  KEY `idx_module_kpi_version_list` (`system_id`,`tenant_id`,`kpi_id`,`version_no` DESC),
  KEY `idx_module_kpi_version_fingerprint` (`system_id`,`tenant_id`,`kpi_id`,`snapshot_fingerprint`,`version_no` DESC),
  KEY `idx_module_kpi_version_source` (`system_id`,`tenant_id`,`data_source_id`,`data_source_version_id`,`data_source_version_no`),
  KEY `fk_module_kpi_version_publisher` (`system_id`,`published_by_member_id`),
  CONSTRAINT `fk_module_kpi_version_publisher` FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_version_root` FOREIGN KEY (`system_id`, `tenant_id`, `kpi_id`) REFERENCES `un_module_kpi` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_kpi_version_source` FOREIGN KEY (`system_id`, `tenant_id`, `data_source_id`, `data_source_version_id`, `data_source_version_no`) REFERENCES `un_module_data_source_version` (`system_id`, `tenant_id`, `data_source_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_kpi_version_code` CHECK ((regexp_like(`kpi_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`data_source_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`module_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,99}$'))),
  CONSTRAINT `ck_module_kpi_version_identity` CHECK (((`id` > 0) and (`kpi_id` > 0) and (`version_no` > 0) and (`source_draft_version` > 0) and (`published_by_member_id` > 0) and (`data_source_id` > 0) and (`data_source_version_id` > 0) and (`data_source_version_no` > 0) and (`module_id` > 0))),
  CONSTRAINT `ck_module_kpi_version_name` CHECK (((char_length(trim(`kpi_name`)) between 1 and 200) and ((`description` is null) or (char_length(trim(`description`)) between 1 and 2000)))),
  CONSTRAINT `ck_module_kpi_version_shape` CHECK (((`subject_type` in (_utf8mb4'MEMBER',_utf8mb4'DEPARTMENT',_utf8mb4'ROLE')) and (`period_type` in (_utf8mb4'MONTH',_utf8mb4'QUARTER',_utf8mb4'YEAR')) and (`attainment_direction` in (_utf8mb4'AT_LEAST',_utf8mb4'AT_MOST')) and regexp_like(`warning_threshold`,_utf8mb4'^(1|0[.][0-9]*[1-9])$') and (`aggregation` in (_utf8mb4'COUNT',_utf8mb4'SUM',_utf8mb4'AVG',_utf8mb4'MIN',_utf8mb4'MAX')) and (((`aggregation` = _utf8mb4'COUNT') and (`measure_field_id` is null) and (`measure_field_code` is null) and (`measure_field_name` is null) and (`measure_field_type` is null) and (`measure_query_type` is null)) or ((`aggregation` <> _utf8mb4'COUNT') and (`measure_field_id` > 0) and regexp_like(`measure_field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (char_length(trim(`measure_field_name`)) between 1 and 200) and regexp_like(`measure_field_type`,_utf8mb4'^[A-Z][A-Z0-9_]{0,99}$') and regexp_like(`measure_query_type`,_utf8mb4'^[A-Z][A-Z0-9_]{0,99}$'))))),
  CONSTRAINT `ck_module_kpi_version_snapshot` CHECK (((char_length(trim(`schema_version_id`)) between 1 and 200) and regexp_like(`calculator_version`,_utf8mb4'^[A-Za-z][A-Za-z0-9_.-]{0,31}$') and (json_type(`snapshot_json`) = _utf8mb4'OBJECT') and (length(`snapshot_json`) between 2 and 262144) and regexp_like(`snapshot_fingerprint`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_module_kpi_version_time` CHECK (((`time_field_id` > 0) and regexp_like(`time_field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and (char_length(trim(`time_field_name`)) between 1 and 200) and regexp_like(`time_field_type`,_utf8mb4'^[A-Z][A-Z0-9_]{0,99}$') and (`time_query_type` in (_utf8mb4'DATE',_utf8mb4'DATETIME'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_kpi_version`
--

LOCK TABLES `un_module_kpi_version` WRITE;
/*!40000 ALTER TABLE `un_module_kpi_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_kpi_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_page`
--

DROP TABLE IF EXISTS `un_module_page`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_page` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `module_id` bigint NOT NULL,
  `page_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `page_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `page_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `layout_json` json NOT NULL,
  `active_marker` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when ((`is_default` = 1) and (`desired_status` = _utf8mb4'ENABLED') and (`deleted_at` is null)) then concat(`system_id`,_utf8mb4':',`module_id`,_utf8mb4':',`page_type`) else NULL end)) STORED,
  `code_locked_at` datetime(3) DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_page_module_code` (`system_id`,`module_id`,`page_code`),
  UNIQUE KEY `uk_module_page_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_module_page_default` (`active_marker`),
  KEY `idx_module_page_list` (`system_id`,`module_id`,`page_type`,`desired_status`,`id`),
  CONSTRAINT `fk_module_page_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `ck_module_page_code` CHECK (regexp_like(`page_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_page_default` CHECK ((`is_default` in (0,1))),
  CONSTRAINT `ck_module_page_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_page_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_page_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_module_page_type` CHECK ((`page_type` in (_utf8mb4'LIST',_utf8mb4'FORM',_utf8mb4'DETAIL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_page`
--

LOCK TABLES `un_module_page` WRITE;
/*!40000 ALTER TABLE `un_module_page` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_page` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_page_component`
--

DROP TABLE IF EXISTS `un_module_page_component`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_page_component` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `page_id` bigint NOT NULL,
  `parent_component_id` bigint DEFAULT NULL,
  `field_id` bigint DEFAULT NULL,
  `component_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `component_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `grid_row` int NOT NULL DEFAULT '0',
  `grid_column` int NOT NULL DEFAULT '0',
  `grid_span` int NOT NULL DEFAULT '12',
  `property_json` json NOT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_page_component_key` (`system_id`,`page_id`,`component_key`),
  UNIQUE KEY `uk_module_page_component_sid` (`system_id`,`id`),
  UNIQUE KEY `uk_module_page_component_page_id` (`system_id`,`page_id`,`id`),
  KEY `idx_module_page_component_tree` (`system_id`,`page_id`,`parent_component_id`,`sort_order`,`id`),
  KEY `idx_module_page_component_field` (`system_id`,`field_id`),
  KEY `fk_module_page_component_parent` (`system_id`,`parent_component_id`),
  CONSTRAINT `fk_module_page_component_field` FOREIGN KEY (`system_id`, `field_id`) REFERENCES `un_module_field` (`system_id`, `id`),
  CONSTRAINT `fk_module_page_component_page` FOREIGN KEY (`system_id`, `page_id`) REFERENCES `un_module_page` (`system_id`, `id`),
  CONSTRAINT `fk_module_page_component_parent_page` FOREIGN KEY (`system_id`, `page_id`, `parent_component_id`) REFERENCES `un_module_page_component` (`system_id`, `page_id`, `id`),
  CONSTRAINT `ck_module_page_component_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_page_component_grid` CHECK (((`grid_row` >= 0) and (`grid_column` >= 0) and (`grid_column` <= 23) and (`grid_span` >= 1) and (`grid_span` <= 24))),
  CONSTRAINT `ck_module_page_component_key` CHECK (regexp_like(`component_key`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_page_component_parent_self` CHECK (((`parent_component_id` is null) or (`parent_component_id` <> `id`))),
  CONSTRAINT `ck_module_page_component_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_page_component_type` CHECK ((`component_type` in (_utf8mb4'FIELD',_utf8mb4'SECTION',_utf8mb4'TABS',_utf8mb4'TAB',_utf8mb4'ACTION',_utf8mb4'TEXT',_utf8mb4'DIVIDER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_page_component`
--

LOCK TABLES `un_module_page_component` WRITE;
/*!40000 ALTER TABLE `un_module_page_component` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_page_component` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_permission`
--

DROP TABLE IF EXISTS `un_module_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_permission` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `module_id` bigint DEFAULT NULL,
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_id` bigint NOT NULL,
  `permission_code` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `registered_permission_id` bigint DEFAULT NULL,
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_permission_code` (`system_id`,`permission_code`),
  UNIQUE KEY `uk_module_permission_resource` (`system_id`,`resource_type`,`resource_id`,`permission_code`),
  UNIQUE KEY `uk_module_permission_system_id` (`system_id`,`id`),
  KEY `idx_module_permission_module` (`system_id`,`module_id`,`desired_status`,`permission_type`),
  CONSTRAINT `fk_module_permission_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `ck_module_permission_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_permission_resource` CHECK ((`resource_type` in (_utf8mb4'MODULE',_utf8mb4'FIELD',_utf8mb4'ACTION',_utf8mb4'PAGE'))),
  CONSTRAINT `ck_module_permission_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_permission_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_module_permission_type` CHECK ((`permission_type` in (_utf8mb4'MENU',_utf8mb4'ACTION',_utf8mb4'FIELD')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_permission`
--

LOCK TABLES `un_module_permission` WRITE;
/*!40000 ALTER TABLE `un_module_permission` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_print_task`
--

DROP TABLE IF EXISTS `un_module_print_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_print_task` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_id` bigint NOT NULL,
  `record_version` bigint NOT NULL,
  `record_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_id` bigint NOT NULL,
  `template_version_id` bigint NOT NULL,
  `template_version_no` bigint NOT NULL,
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `snapshot_json` json NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_filename` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result_content` longblob,
  `result_size` bigint DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `job_id` bigint NOT NULL,
  `requested_by_account_id` bigint NOT NULL,
  `requested_by_member_id` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_print_job` (`job_id`),
  KEY `idx_module_print_history` (`system_id`,`tenant_id`,`logical_module_id`,`record_id`,`requested_by_member_id`,`created_at`),
  KEY `idx_module_print_status` (`status`,`updated_at`),
  KEY `fk_module_print_template_version` (`system_id`,`template_version_id`),
  KEY `idx_module_print_task_record_history` (`system_id`,`tenant_id`,`module_code`,`record_id`,`requested_by_member_id`,`created_at`,`id`),
  CONSTRAINT `fk_module_print_template_version` FOREIGN KEY (`system_id`, `template_version_id`) REFERENCES `un_module_print_template_version` (`system_id`, `id`),
  CONSTRAINT `ck_module_print_record_version` CHECK (((`record_version` > 0) and (`template_version_no` > 0))),
  CONSTRAINT `ck_module_print_snapshot_size` CHECK ((length(`snapshot_json`) <= 524288)),
  CONSTRAINT `ck_module_print_status` CHECK ((`status` in (_utf8mb4'QUEUED',_utf8mb4'RUNNING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_print_task`
--

LOCK TABLES `un_module_print_task` WRITE;
/*!40000 ALTER TABLE `un_module_print_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_print_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_print_template`
--

DROP TABLE IF EXISTS `un_module_print_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_print_template` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `paper_size` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `orientation` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `definition_json` json NOT NULL,
  `published_version_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_code` (`system_id`,`logical_module_id`,`template_code`),
  UNIQUE KEY `uk_print_template_system_id` (`system_id`,`id`),
  KEY `idx_print_template_module` (`system_id`,`logical_module_id`,`desired_status`,`updated_at`),
  KEY `fk_print_template_published` (`system_id`,`published_version_id`),
  CONSTRAINT `fk_print_template_module` FOREIGN KEY (`system_id`, `logical_module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `fk_print_template_published` FOREIGN KEY (`system_id`, `published_version_id`) REFERENCES `un_module_print_template_version` (`system_id`, `id`),
  CONSTRAINT `ck_print_template_code` CHECK (regexp_like(`template_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_print_template_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_print_template_orientation` CHECK ((`orientation` in (_utf8mb4'PORTRAIT',_utf8mb4'LANDSCAPE'))),
  CONSTRAINT `ck_print_template_paper` CHECK ((`paper_size` in (_utf8mb4'A4',_utf8mb4'A5'))),
  CONSTRAINT `ck_print_template_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_print_template`
--

LOCK TABLES `un_module_print_template` WRITE;
/*!40000 ALTER TABLE `un_module_print_template` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_print_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_print_template_version`
--

DROP TABLE IF EXISTS `un_module_print_template_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_print_template_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_no` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `paper_size` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `orientation` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `definition_json` json NOT NULL,
  `definition_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `published_at` datetime(3) NOT NULL,
  `published_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_version_no` (`system_id`,`template_id`,`version_no`),
  UNIQUE KEY `uk_print_template_version_system_id` (`system_id`,`id`),
  KEY `idx_print_version_schema` (`system_id`,`schema_version_id`,`module_snapshot_id`),
  CONSTRAINT `fk_print_version_schema` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_runtime_schema_module` (`system_id`, `schema_version_id`, `module_snapshot_id`),
  CONSTRAINT `fk_print_version_template` FOREIGN KEY (`system_id`, `template_id`) REFERENCES `un_module_print_template` (`system_id`, `id`),
  CONSTRAINT `ck_print_template_version_no` CHECK ((`version_no` > 0)),
  CONSTRAINT `ck_print_version_checksum` CHECK (regexp_like(`definition_checksum`,_utf8mb4'^[a-f0-9]{64}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_print_template_version`
--

LOCK TABLES `un_module_print_template_version` WRITE;
/*!40000 ALTER TABLE `un_module_print_template_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_print_template_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_publish_record`
--

DROP TABLE IF EXISTS `un_module_publish_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_publish_record` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `operation_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `from_version_id` bigint DEFAULT NULL,
  `to_version_id` bigint NOT NULL,
  `target_version_id` bigint DEFAULT NULL,
  `check_id` bigint NOT NULL,
  `draft_revision` bigint NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `impact_report_json` json NOT NULL,
  `operated_at` datetime(3) NOT NULL,
  `operated_by` bigint NOT NULL,
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_publish_idempotency` (`system_id`,`idempotency_key`),
  UNIQUE KEY `uk_module_publish_to_version` (`system_id`,`to_version_id`),
  KEY `idx_module_publish_record_time` (`system_id`,`operated_at` DESC,`id`),
  KEY `fk_module_publish_from` (`system_id`,`from_version_id`),
  KEY `fk_module_publish_target` (`system_id`,`target_version_id`),
  KEY `fk_module_publish_check` (`system_id`,`check_id`),
  CONSTRAINT `fk_module_publish_check` FOREIGN KEY (`system_id`, `check_id`) REFERENCES `un_module_config_check` (`system_id`, `id`),
  CONSTRAINT `fk_module_publish_from` FOREIGN KEY (`system_id`, `from_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `fk_module_publish_target` FOREIGN KEY (`system_id`, `target_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `fk_module_publish_to` FOREIGN KEY (`system_id`, `to_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`),
  CONSTRAINT `ck_module_publish_operation` CHECK ((`operation_type` in (_utf8mb4'PUBLISH',_utf8mb4'ROLLBACK'))),
  CONSTRAINT `ck_module_publish_result` CHECK ((`result` = _utf8mb4'SUCCEEDED')),
  CONSTRAINT `ck_module_publish_revision` CHECK ((`draft_revision` >= 0)),
  CONSTRAINT `ck_module_publish_target` CHECK ((((`operation_type` = _utf8mb4'PUBLISH') and (`target_version_id` is null)) or ((`operation_type` = _utf8mb4'ROLLBACK') and (`target_version_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_publish_record`
--

LOCK TABLES `un_module_publish_record` WRITE;
/*!40000 ALTER TABLE `un_module_publish_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_publish_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_recent`
--

DROP TABLE IF EXISTS `un_module_recent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_recent` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `record_schema_version_id` bigint NOT NULL,
  `record_module_snapshot_id` bigint NOT NULL,
  `access_count` bigint NOT NULL DEFAULT '1',
  `last_accessed_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recent_record` (`system_id`,`tenant_id`,`member_id`,`logical_module_id`,`record_id`),
  KEY `idx_recent_member` (`system_id`,`tenant_id`,`member_id`,`last_accessed_at` DESC,`id` DESC),
  KEY `fk_recent_member` (`system_id`,`member_id`),
  KEY `fk_recent_module` (`system_id`,`module_schema_version_id`,`module_snapshot_id`),
  KEY `fk_recent_record` (`system_id`,`tenant_id`,`record_id`,`record_schema_version_id`,`record_module_snapshot_id`),
  CONSTRAINT `fk_recent_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recent_module` FOREIGN KEY (`system_id`, `module_schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_runtime_schema_module` (`system_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recent_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `record_schema_version_id`, `record_module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recent_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_recent_access_count` CHECK ((`access_count` > 0)),
  CONSTRAINT `ck_recent_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_recent`
--

LOCK TABLES `un_module_recent` WRITE;
/*!40000 ALTER TABLE `un_module_recent` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_recent` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record`
--

DROP TABLE IF EXISTS `un_module_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `record_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `prior_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `owner_member_id` bigint NOT NULL,
  `owner_department_id` bigint DEFAULT NULL,
  `draft_expires_at` datetime(3) DEFAULT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_identity` (`system_id`,`tenant_id`,`record_id`,`schema_version_id`,`module_snapshot_id`),
  UNIQUE KEY `uk_record_logical` (`system_id`,`tenant_id`,`logical_module_id`,`record_id`),
  UNIQUE KEY `uk_record_collab_scope` (`system_id`,`tenant_id`,`record_id`),
  KEY `idx_record_list` (`system_id`,`tenant_id`,`logical_module_id`,`status`,`record_id`),
  KEY `idx_record_owner` (`system_id`,`tenant_id`,`logical_module_id`,`owner_member_id`,`status`,`record_id`),
  KEY `idx_record_department` (`system_id`,`tenant_id`,`logical_module_id`,`owner_department_id`,`status`,`record_id`),
  KEY `fk_record_owner` (`system_id`,`owner_member_id`),
  KEY `idx_record_draft_expiry` (`status`,`draft_expires_at`,`record_id`),
  KEY `fk_record_runtime_module` (`system_id`,`schema_version_id`,`module_snapshot_id`),
  CONSTRAINT `fk_record_owner` FOREIGN KEY (`system_id`, `owner_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_runtime_module` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_runtime_schema_module` (`system_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_schema_version` FOREIGN KEY (`system_id`, `schema_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_record_draft_expiry` CHECK ((((`status` = _utf8mb4'DRAFT') and (`draft_expires_at` is not null)) or ((`status` <> _utf8mb4'DRAFT') and (`draft_expires_at` is null)))),
  CONSTRAINT `ck_record_identity` CHECK ((`id` = `record_id`)),
  CONSTRAINT `ck_record_prior_status` CHECK ((((`status` = _utf8mb4'TRASHED') and (`prior_status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED',_utf8mb4'EXPIRED')) and (`deleted_at` is not null)) or ((`status` <> _utf8mb4'TRASHED') and (`prior_status` is null) and (`deleted_at` is null)))),
  CONSTRAINT `ck_record_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED',_utf8mb4'TRASHED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `ck_record_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record`
--

LOCK TABLES `un_module_record` WRITE;
/*!40000 ALTER TABLE `un_module_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_flow_state`
--

DROP TABLE IF EXISTS `un_module_record_flow_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_flow_state` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `status_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_approved_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_rejected_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_withdrawn_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_terminated_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`record_id`),
  UNIQUE KEY `uk_record_flow_instance` (`system_id`,`tenant_id`,`instance_id`),
  KEY `idx_record_flow_record_fk` (`system_id`,`tenant_id`,`logical_module_id`,`record_id`),
  KEY `idx_record_flow_status` (`system_id`,`tenant_id`,`logical_module_id`,`status`,`record_id`),
  CONSTRAINT `fk_record_flow_record` FOREIGN KEY (`system_id`, `tenant_id`, `logical_module_id`, `record_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `logical_module_id`, `record_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_record_flow_identity` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`record_id` > 0) and (`logical_module_id` > 0) and (`instance_id` > 0) and (`created_by` > 0) and (`updated_by` > 0))),
  CONSTRAINT `ck_record_flow_status` CHECK ((`status` in (_ascii'PENDING',_ascii'APPROVED',_ascii'REJECTED',_ascii'WITHDRAWN',_ascii'TERMINATED'))),
  CONSTRAINT `ck_record_flow_status_mapping_presence` CHECK ((((`status_field_code` is null) and (`status_approved_value` is null) and (`status_rejected_value` is null) and (`status_withdrawn_value` is null) and (`status_terminated_value` is null)) or ((`status_field_code` is not null) and (`status_approved_value` is not null) and (`status_rejected_value` is not null) and (`status_withdrawn_value` is not null) and (`status_terminated_value` is not null)))),
  CONSTRAINT `ck_record_flow_status_mapping_syntax` CHECK (((`status_field_code` is null) or (regexp_like(`status_field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`status_approved_value`,_utf8mb4'^[1-9][0-9]{0,18}$') and regexp_like(`status_rejected_value`,_utf8mb4'^[1-9][0-9]{0,18}$') and regexp_like(`status_withdrawn_value`,_utf8mb4'^[1-9][0-9]{0,18}$') and regexp_like(`status_terminated_value`,_utf8mb4'^[1-9][0-9]{0,18}$')))),
  CONSTRAINT `ck_record_flow_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_flow_state`
--

LOCK TABLES `un_module_record_flow_state` WRITE;
/*!40000 ALTER TABLE `un_module_record_flow_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_flow_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_flow_state_item`
--

DROP TABLE IF EXISTS `un_module_record_flow_state_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_flow_state_item` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `event_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `status_field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_approved_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_rejected_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_withdrawn_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status_terminated_value` varchar(19) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`record_id`,`instance_id`),
  UNIQUE KEY `uk_record_flow_item_instance` (`system_id`,`tenant_id`,`instance_id`),
  KEY `idx_record_flow_item_record_fk` (`system_id`,`tenant_id`,`logical_module_id`,`record_id`),
  KEY `idx_record_flow_item_page` (`system_id`,`tenant_id`,`record_id`,`created_at`,`instance_id`),
  KEY `idx_record_flow_item_pending` (`system_id`,`tenant_id`,`record_id`,`status`,`instance_id`),
  KEY `idx_record_flow_item_event` (`system_id`,`tenant_id`,`event_key`,`instance_id`),
  CONSTRAINT `fk_record_flow_item_record` FOREIGN KEY (`system_id`, `tenant_id`, `logical_module_id`, `record_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `logical_module_id`, `record_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_record_flow_item_event_key` CHECK ((char_length(trim(`event_key`)) between 1 and 200)),
  CONSTRAINT `ck_record_flow_item_identity` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`record_id` > 0) and (`logical_module_id` > 0) and (`instance_id` > 0) and (`created_by` > 0) and (`updated_by` > 0))),
  CONSTRAINT `ck_record_flow_item_mapping_presence` CHECK ((((`status_field_code` is null) and (`status_approved_value` is null) and (`status_rejected_value` is null) and (`status_withdrawn_value` is null) and (`status_terminated_value` is null)) or ((`status_field_code` is not null) and (`status_approved_value` is not null) and (`status_rejected_value` is not null) and (`status_withdrawn_value` is not null) and (`status_terminated_value` is not null)))),
  CONSTRAINT `ck_record_flow_item_mapping_syntax` CHECK (((`status_field_code` is null) or (regexp_like(`status_field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`status_approved_value`,_utf8mb4'^[1-9][0-9]{0,18}$') and regexp_like(`status_rejected_value`,_utf8mb4'^[1-9][0-9]{0,18}$') and regexp_like(`status_withdrawn_value`,_utf8mb4'^[1-9][0-9]{0,18}$') and regexp_like(`status_terminated_value`,_utf8mb4'^[1-9][0-9]{0,18}$')))),
  CONSTRAINT `ck_record_flow_item_status` CHECK ((`status` in (_ascii'PENDING',_ascii'APPROVED',_ascii'REJECTED',_ascii'WITHDRAWN',_ascii'TERMINATED'))),
  CONSTRAINT `ck_record_flow_item_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_flow_state_item`
--

LOCK TABLES `un_module_record_flow_state_item` WRITE;
/*!40000 ALTER TABLE `un_module_record_flow_state_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_flow_state_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_history`
--

DROP TABLE IF EXISTS `un_module_record_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_history` (
  `history_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `record_version` bigint NOT NULL,
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_member_id` bigint DEFAULT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `diff_json` json NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_module_record_history_event` (`system_id`,`tenant_id`,`record_id`,`record_version`,`action`),
  KEY `idx_module_record_history_page` (`system_id`,`tenant_id`,`record_id`,`occurred_at`,`history_id`),
  KEY `fk_module_record_history_actor` (`system_id`,`actor_member_id`,`tenant_id`),
  CONSTRAINT `fk_module_record_history_actor` FOREIGN KEY (`system_id`, `actor_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_record_history_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_record_history_action` CHECK (regexp_like(`action`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_record_history_version` CHECK ((`record_version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_history`
--

LOCK TABLES `un_module_record_history` WRITE;
/*!40000 ALTER TABLE `un_module_record_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_index`
--

DROP TABLE IF EXISTS `un_module_record_index`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_index` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `index_generation_id` bigint NOT NULL DEFAULT '1',
  `normalization_generation_id` bigint NOT NULL DEFAULT '1',
  `path_snapshot_id` bigint NOT NULL DEFAULT '0',
  `ordinal` int NOT NULL DEFAULT '0',
  `record_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value_kind` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `string_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `decimal_value` decimal(38,10) DEFAULT NULL,
  `date_value` date DEFAULT NULL,
  `datetime_value` datetime(3) DEFAULT NULL,
  `time_value` time DEFAULT NULL,
  `boolean_value` tinyint(1) DEFAULT NULL,
  `reference_value` bigint DEFAULT NULL,
  `hash_value` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `hash_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `geohash` varchar(12) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `geo_lat` double DEFAULT NULL,
  `geo_lng` double DEFAULT NULL,
  `hash_version_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (coalesce(`hash_key_version`,_ascii'-')) STORED,
  `currency_code` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_index_ordinal` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_id`,`ordinal`,`path_snapshot_id`,`hash_version_key`),
  KEY `idx_ri_typed_routes` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`value_kind`,`record_id`),
  KEY `idx_ri_string` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`string_value`,`record_id`),
  KEY `idx_ri_decimal` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`decimal_value`,`record_id`),
  KEY `idx_ri_datetime` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`datetime_value`,`record_id`),
  KEY `idx_ri_reference` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`reference_value`,`record_id`),
  KEY `fk_ri_record` (`system_id`,`tenant_id`,`record_id`,`schema_version_id`,`module_snapshot_id`),
  KEY `idx_ri_time` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`time_value`,`record_id`),
  KEY `idx_ri_hash` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`hash_key_version`,`hash_value`,`record_id`),
  KEY `idx_ri_json_path` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`path_snapshot_id`,`value_kind`,`record_id`),
  KEY `idx_ri_geo` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`geohash`,`geo_lat`,`geo_lng`,`record_id`),
  KEY `idx_ri_geo_bounds` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`geo_lat`,`geo_lng`,`record_id`),
  CONSTRAINT `fk_ri_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ri_generations` CHECK (((`index_generation_id` > 0) and (`normalization_generation_id` > 0))),
  CONSTRAINT `ck_ri_ordinal` CHECK ((`ordinal` between 0 and 99)),
  CONSTRAINT `ck_ri_record_status` CHECK ((`record_status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED',_utf8mb4'TRASHED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `ck_ri_typed_value` CHECK ((((`value_kind` = _utf8mb4'STRING') and (`string_value` is not null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'DECIMAL') and (`decimal_value` is not null) and (`string_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'DATE') and (`date_value` is not null) and (`string_value` is null) and (`decimal_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'DATETIME') and (`datetime_value` is not null) and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'TIME') and (`time_value` is not null) and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'BOOLEAN') and (`boolean_value` is not null) and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'REFERENCE') and (`reference_value` is not null) and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'HASH') and (`hash_value` is not null) and (`hash_key_version` is not null) and regexp_like(`hash_value`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`hash_key_version`,_utf8mb4'^[A-Za-z0-9._-]{1,64}$') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'MONEY') and (`decimal_value` is not null) and (`currency_code` is not null) and (`string_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null)) or ((`value_kind` = _utf8mb4'GEO') and (`geohash` is not null) and (`geo_lat` is not null) and (`geo_lng` is not null) and regexp_like(`geohash`,_utf8mb4'^[0-9bcdefghjkmnpqrstuvwxyz]{12}$') and (`geo_lat` between -(90) and 90) and (`geo_lng` between -(180) and 180) and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`currency_code` is null)) or ((`value_kind` = _utf8mb4'NULL') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null) and (`hash_value` is null) and (`hash_key_version` is null) and (`geohash` is null) and (`geo_lat` is null) and (`geo_lng` is null) and (`currency_code` is null)))),
  CONSTRAINT `ck_ri_value_kind` CHECK ((`value_kind` in (_utf8mb4'STRING',_utf8mb4'DECIMAL',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'TIME',_utf8mb4'BOOLEAN',_utf8mb4'REFERENCE',_utf8mb4'HASH',_utf8mb4'MONEY',_utf8mb4'GEO',_utf8mb4'NULL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_index`
--

LOCK TABLES `un_module_record_index` WRITE;
/*!40000 ALTER TABLE `un_module_record_index` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_index` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_relation`
--

DROP TABLE IF EXISTS `un_module_record_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_relation` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_record_id` bigint NOT NULL,
  `source_schema_version_id` bigint NOT NULL,
  `source_module_snapshot_id` bigint NOT NULL,
  `source_logical_module_id` bigint NOT NULL,
  `source_field_snapshot_id` bigint NOT NULL,
  `source_logical_field_id` bigint NOT NULL,
  `source_field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RELATION',
  `source_field_scope` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RECORD',
  `target_record_id` bigint NOT NULL,
  `target_schema_version_id` bigint NOT NULL,
  `target_module_snapshot_id` bigint NOT NULL,
  `target_logical_module_id` bigint NOT NULL,
  `ordinal` int NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_relation_target` (`system_id`,`tenant_id`,`source_record_id`,`source_schema_version_id`,`source_module_snapshot_id`,`source_field_snapshot_id`,`target_record_id`),
  UNIQUE KEY `uk_relation_ordinal` (`system_id`,`tenant_id`,`source_record_id`,`source_field_snapshot_id`,`ordinal`),
  KEY `idx_relation_target` (`system_id`,`tenant_id`,`target_record_id`),
  KEY `fk_relation_source_field` (`system_id`,`source_schema_version_id`,`source_module_snapshot_id`,`source_field_snapshot_id`,`source_field_type`,`source_field_scope`),
  KEY `fk_relation_target_record` (`system_id`,`tenant_id`,`target_record_id`,`target_schema_version_id`,`target_module_snapshot_id`),
  CONSTRAINT `fk_relation_source_field` FOREIGN KEY (`system_id`, `source_schema_version_id`, `source_module_snapshot_id`, `source_field_snapshot_id`, `source_field_type`, `source_field_scope`) REFERENCES `un_module_runtime_schema_field` (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`, `field_type`, `field_scope`) ON DELETE RESTRICT,
  CONSTRAINT `fk_relation_source_record` FOREIGN KEY (`system_id`, `tenant_id`, `source_record_id`, `source_schema_version_id`, `source_module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_relation_target_record` FOREIGN KEY (`system_id`, `tenant_id`, `target_record_id`, `target_schema_version_id`, `target_module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_relation_ordinal` CHECK ((`ordinal` between 0 and 499)),
  CONSTRAINT `ck_relation_source` CHECK (((`source_field_type` = _utf8mb4'RELATION') and (`source_field_scope` = _utf8mb4'RECORD'))),
  CONSTRAINT `ck_relation_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_relation`
--

LOCK TABLES `un_module_record_relation` WRITE;
/*!40000 ALTER TABLE `un_module_record_relation` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_relation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_search`
--

DROP TABLE IF EXISTS `un_module_record_search`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_search` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `index_generation_id` bigint NOT NULL DEFAULT '1',
  `record_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_ordinal` int NOT NULL,
  `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_search_token` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_id`,`token_ordinal`,`token_hash`),
  KEY `idx_rs_query` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`index_generation_id`,`record_status`,`token_hash`,`record_id`),
  KEY `fk_rs_record` (`system_id`,`tenant_id`,`record_id`,`schema_version_id`,`module_snapshot_id`),
  CONSTRAINT `fk_rs_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_rs_field_type` CHECK ((`field_type` in (_utf8mb4'TEXT',_utf8mb4'TEXTAREA',_utf8mb4'RICH_TEXT'))),
  CONSTRAINT `ck_rs_generation` CHECK ((`index_generation_id` > 0)),
  CONSTRAINT `ck_rs_hash` CHECK (regexp_like(`token_hash`,_utf8mb4'^[a-f0-9]{64}$')),
  CONSTRAINT `ck_rs_ordinal` CHECK ((`token_ordinal` between 0 and 255)),
  CONSTRAINT `ck_rs_status` CHECK ((`record_status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED',_utf8mb4'TRASHED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `ck_rs_token` CHECK ((char_length(`token`) between 1 and 255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_search`
--

LOCK TABLES `un_module_record_search` WRITE;
/*!40000 ALTER TABLE `un_module_record_search` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_search` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_unique`
--

DROP TABLE IF EXISTS `un_module_record_unique`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_unique` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `normalization_generation_id` bigint NOT NULL DEFAULT '1',
  `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency_code` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency_key` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS (coalesce(`currency_code`,_utf8mb4'---')) STORED,
  `normalized_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `hash_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `hash_version_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (coalesce(`hash_key_version`,_ascii'-')) STORED,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_unique_value` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`normalization_generation_id`,`currency_key`,`hash_version_key`,`normalized_hash`),
  KEY `idx_ru_record` (`system_id`,`tenant_id`,`record_id`),
  KEY `fk_ru_record` (`system_id`,`tenant_id`,`record_id`,`schema_version_id`,`module_snapshot_id`),
  CONSTRAINT `fk_ru_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ru_currency` CHECK ((((`field_type` = _utf8mb4'MONEY') and regexp_like(`currency_code`,_utf8mb4'^[A-Z]{3}$')) or ((`field_type` <> _utf8mb4'MONEY') and (`currency_code` is null)))),
  CONSTRAINT `ck_ru_generation` CHECK ((`normalization_generation_id` > 0)),
  CONSTRAINT `ck_ru_hash_version` CHECK ((((`field_type` in (_utf8mb4'IDENTITY',_utf8mb4'SECRET')) and (`hash_key_version` is not null) and regexp_like(`hash_key_version`,_ascii'^[A-Za-z0-9._-]{1,64}$')) or ((`field_type` not in (_utf8mb4'IDENTITY',_utf8mb4'SECRET')) and (`hash_key_version` is null)))),
  CONSTRAINT `ck_ru_record_status` CHECK ((`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_ru_supported_type` CHECK ((`field_type` in (_utf8mb4'PERCENT',_utf8mb4'MONEY',_utf8mb4'TIME',_utf8mb4'SWITCH',_utf8mb4'RATING',_utf8mb4'PROGRESS',_utf8mb4'PHONE',_utf8mb4'EMAIL',_utf8mb4'URL',_utf8mb4'IDENTITY',_utf8mb4'BARCODE',_utf8mb4'SECRET',_utf8mb4'AUTO_NUMBER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_unique`
--

LOCK TABLES `un_module_record_unique` WRITE;
/*!40000 ALTER TABLE `un_module_record_unique` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_unique` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_record_value`
--

DROP TABLE IF EXISTS `un_module_record_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_record_value` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `field_snapshot_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `field_version` bigint NOT NULL DEFAULT '1',
  `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_scope` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RECORD',
  `result_schema` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dependency_version_json` json DEFAULT NULL,
  `evaluator_version` smallint unsigned DEFAULT NULL,
  `recalculation_state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_correlation_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `ordinal` int NOT NULL DEFAULT '0',
  `string_value` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `decimal_value` decimal(38,10) DEFAULT NULL,
  `date_value` date DEFAULT NULL,
  `datetime_value` datetime(3) DEFAULT NULL,
  `time_value` time DEFAULT NULL,
  `boolean_value` tinyint(1) DEFAULT NULL,
  `currency_code` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_value` bigint DEFAULT NULL,
  `encrypted_value` blob,
  `encryption_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `value_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hash_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `display_value` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_value_ordinal` (`system_id`,`tenant_id`,`record_id`,`schema_version_id`,`module_snapshot_id`,`field_snapshot_id`,`ordinal`),
  KEY `idx_rv_logical` (`system_id`,`tenant_id`,`logical_module_id`,`logical_field_id`,`record_id`),
  KEY `idx_rv_derived_state` (`system_id`,`tenant_id`,`recalculation_state`,`record_id`,`field_snapshot_id`),
  KEY `fk_rv_runtime_result` (`system_id`,`schema_version_id`,`module_snapshot_id`,`field_snapshot_id`,`field_type`,`field_scope`,`result_schema`),
  CONSTRAINT `fk_rv_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_rv_runtime_field` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`) REFERENCES `un_module_runtime_schema_field` (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_rv_runtime_result` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`, `field_type`, `field_scope`, `result_schema`) REFERENCES `un_module_runtime_schema_field` (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`, `field_type`, `field_scope`, `result_schema`) ON DELETE RESTRICT,
  CONSTRAINT `ck_rv_currency_code` CHECK (((`currency_code` is null) or regexp_like(`currency_code`,_utf8mb4'^[A-Z]{3}$'))),
  CONSTRAINT `ck_rv_derived_metadata` CHECK ((((`field_type` in (_utf8mb4'FORMULA',_utf8mb4'SUMMARY',_utf8mb4'CALCULATED',_utf8mb4'LOOKUP',_utf8mb4'AGGREGATE')) and (`field_scope` = _utf8mb4'RECORD') and (`result_schema` in (_utf8mb4'STRING',_utf8mb4'DECIMAL',_utf8mb4'INTEGER',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'BOOLEAN')) and (`dependency_version_json` is not null) and (json_type(`dependency_version_json`) = _utf8mb4'ARRAY') and (length(`dependency_version_json`) <= 32768) and (`evaluator_version` = 1) and (`recalculation_state` in (_utf8mb4'READY',_utf8mb4'PENDING',_utf8mb4'FAILED')) and (((`recalculation_state` = _utf8mb4'FAILED') and (`failure_correlation_id` is not null) and regexp_like(`failure_correlation_id`,_ascii'^[A-Za-z0-9._:-]{1,64}$')) or ((`recalculation_state` <> _utf8mb4'FAILED') and (`failure_correlation_id` is null)))) or ((`field_type` = _utf8mb4'AI_FILL') and (`field_scope` = _utf8mb4'RECORD') and (`result_schema` in (_utf8mb4'STRING',_utf8mb4'DECIMAL',_utf8mb4'INTEGER',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'BOOLEAN')) and (`dependency_version_json` is not null) and (json_type(`dependency_version_json`) = _utf8mb4'OBJECT') and (json_length(`dependency_version_json`) = 1) and regexp_like(json_unquote(json_extract(`dependency_version_json`,_utf8mb4'$.sourceVersionHash')),_utf8mb4'^[0-9a-f]{64}$') and (length(`dependency_version_json`) <= 128) and (`evaluator_version` = 1) and (`recalculation_state` = _utf8mb4'READY') and (`failure_correlation_id` is null)) or ((`field_type` not in (_utf8mb4'FORMULA',_utf8mb4'SUMMARY',_utf8mb4'CALCULATED',_utf8mb4'LOOKUP',_utf8mb4'AGGREGATE',_utf8mb4'AI_FILL')) and (`field_scope` = _utf8mb4'RECORD') and (`result_schema` is null) and (`dependency_version_json` is null) and (`evaluator_version` is null) and (`recalculation_state` is null) and (`failure_correlation_id` is null)))),
  CONSTRAINT `ck_rv_field_version` CHECK ((`field_version` > 0)),
  CONSTRAINT `ck_rv_ordinal` CHECK ((`ordinal` between 0 and 99)),
  CONSTRAINT `ck_rv_sensitive_versions` CHECK ((((`field_type` in (_utf8mb4'IDENTITY',_utf8mb4'SECRET')) and (`encrypted_value` is not null) and (`value_hash` is not null) and (`encryption_key_version` is not null) and (`hash_key_version` is not null) and regexp_like(`encryption_key_version`,_ascii'^[A-Za-z0-9._-]{1,64}$') and regexp_like(`hash_key_version`,_ascii'^[A-Za-z0-9._-]{1,64}$') and (length(`encrypted_value`) between 30 and 20000)) or ((`field_type` not in (_utf8mb4'IDENTITY',_utf8mb4'SECRET')) and (`encrypted_value` is null) and (`value_hash` is null) and (`encryption_key_version` is null) and (`hash_key_version` is null)))),
  CONSTRAINT `ck_rv_supported_type` CHECK ((`field_type` in (_utf8mb4'TEXT',_utf8mb4'TEXTAREA',_utf8mb4'NUMBER',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'RADIO',_utf8mb4'MEMBER',_utf8mb4'DEPARTMENT',_utf8mb4'PERCENT',_utf8mb4'MONEY',_utf8mb4'DATE_RANGE',_utf8mb4'TIME',_utf8mb4'TIME_RANGE',_utf8mb4'MULTI_SELECT',_utf8mb4'CASCADE',_utf8mb4'SWITCH',_utf8mb4'RATING',_utf8mb4'PROGRESS',_utf8mb4'TAG',_utf8mb4'PHONE',_utf8mb4'EMAIL',_utf8mb4'URL',_utf8mb4'IDENTITY',_utf8mb4'ADDRESS',_utf8mb4'GEO',_utf8mb4'BARCODE',_utf8mb4'RICH_TEXT',_utf8mb4'JSON',_utf8mb4'SECRET',_utf8mb4'STATUS',_utf8mb4'REFERENCE',_utf8mb4'ATTACHMENT',_utf8mb4'IMAGE',_utf8mb4'FILE_GROUP',_utf8mb4'SIGNATURE',_utf8mb4'FORMULA',_utf8mb4'SUMMARY',_utf8mb4'CALCULATED',_utf8mb4'LOOKUP',_utf8mb4'AGGREGATE',_utf8mb4'AI_FILL',_utf8mb4'TENANT',_utf8mb4'AUTO_NUMBER',_utf8mb4'CREATED_BY',_utf8mb4'CREATED_AT',_utf8mb4'UPDATED_BY',_utf8mb4'UPDATED_AT'))),
  CONSTRAINT `ck_rv_typed_payload` CHECK ((((`field_type` in (_utf8mb4'TEXT',_utf8mb4'TAG',_utf8mb4'PHONE',_utf8mb4'EMAIL',_utf8mb4'URL',_utf8mb4'AUTO_NUMBER')) and (`string_value` is not null) and (`text_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` in (_utf8mb4'TEXTAREA',_utf8mb4'ADDRESS',_utf8mb4'GEO',_utf8mb4'BARCODE',_utf8mb4'RICH_TEXT',_utf8mb4'JSON')) and (`text_value` is not null) and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` in (_utf8mb4'NUMBER',_utf8mb4'PERCENT',_utf8mb4'RATING',_utf8mb4'PROGRESS')) and (`decimal_value` is not null) and (`string_value` is null) and (`text_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` = _utf8mb4'MONEY') and (`decimal_value` is not null) and (`currency_code` is not null) and (`string_value` is null) and (`text_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`reference_value` is null)) or ((`field_type` in (_utf8mb4'DATE',_utf8mb4'DATE_RANGE')) and (`date_value` is not null) and (`string_value` is null) and (`text_value` is null) and (`decimal_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` in (_utf8mb4'DATETIME',_utf8mb4'CREATED_AT',_utf8mb4'UPDATED_AT')) and (`datetime_value` is not null) and (`string_value` is null) and (`text_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` in (_utf8mb4'TIME',_utf8mb4'TIME_RANGE')) and (`time_value` is not null) and (`string_value` is null) and (`text_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` = _utf8mb4'SWITCH') and (`boolean_value` is not null) and (`string_value` is null) and (`text_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` in (_utf8mb4'RADIO',_utf8mb4'MEMBER',_utf8mb4'DEPARTMENT',_utf8mb4'MULTI_SELECT',_utf8mb4'CASCADE',_utf8mb4'STATUS',_utf8mb4'ATTACHMENT',_utf8mb4'IMAGE',_utf8mb4'FILE_GROUP',_utf8mb4'SIGNATURE',_utf8mb4'TENANT',_utf8mb4'CREATED_BY',_utf8mb4'UPDATED_BY')) and (`reference_value` is not null) and (`string_value` is null) and (`text_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`currency_code` is null)) or ((`field_type` in (_utf8mb4'IDENTITY',_utf8mb4'SECRET')) and (`string_value` is null) and (`text_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`time_value` is null) and (`boolean_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` = _utf8mb4'REFERENCE') and ((((((`string_value` is not null) + (`decimal_value` is not null)) + (`date_value` is not null)) + (`datetime_value` is not null)) + (`boolean_value` is not null)) = 1) and (`text_value` is null) and (`time_value` is null) and (`currency_code` is null) and (`reference_value` is null)) or ((`field_type` in (_utf8mb4'FORMULA',_utf8mb4'SUMMARY',_utf8mb4'CALCULATED',_utf8mb4'LOOKUP',_utf8mb4'AGGREGATE')) and (`text_value` is null) and (`time_value` is null) and (`currency_code` is null) and (`reference_value` is null) and ((((((`string_value` is not null) + (`decimal_value` is not null)) + (`date_value` is not null)) + (`datetime_value` is not null)) + (`boolean_value` is not null)) <= 1) and (((`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is null)) or ((`result_schema` = _utf8mb4'STRING') and (`string_value` is not null)) or ((`result_schema` = _utf8mb4'DECIMAL') and (`decimal_value` is not null)) or ((`result_schema` = _utf8mb4'INTEGER') and (`decimal_value` is not null) and (`decimal_value` = truncate(`decimal_value`,0))) or ((`result_schema` = _utf8mb4'DATE') and (`date_value` is not null)) or ((`result_schema` = _utf8mb4'DATETIME') and (`datetime_value` is not null)) or ((`result_schema` = _utf8mb4'BOOLEAN') and (`boolean_value` is not null)))) or ((`field_type` = _utf8mb4'AI_FILL') and (`text_value` is null) and (`time_value` is null) and (`currency_code` is null) and (`reference_value` is null) and (((`result_schema` = _utf8mb4'STRING') and (`string_value` is not null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is null)) or ((`result_schema` in (_utf8mb4'DECIMAL',_utf8mb4'INTEGER')) and (`string_value` is null) and (`decimal_value` is not null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is null) and ((`result_schema` <> _utf8mb4'INTEGER') or (`decimal_value` = truncate(`decimal_value`,0)))) or ((`result_schema` = _utf8mb4'DATE') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is not null) and (`datetime_value` is null) and (`boolean_value` is null)) or ((`result_schema` = _utf8mb4'DATETIME') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is not null) and (`boolean_value` is null)) or ((`result_schema` = _utf8mb4'BOOLEAN') and (`string_value` is null) and (`decimal_value` is null) and (`date_value` is null) and (`datetime_value` is null) and (`boolean_value` is not null)))))),
  CONSTRAINT `ck_rv_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_record_value`
--

LOCK TABLES `un_module_record_value` WRITE;
/*!40000 ALTER TABLE `un_module_record_value` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_record_value` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_reference_recalc_task`
--

DROP TABLE IF EXISTS `un_module_reference_recalc_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_reference_recalc_task` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_record_id` bigint NOT NULL,
  `source_record_version` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `available_at` datetime(3) NOT NULL,
  `correlation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reference_recalc_source` (`system_id`,`tenant_id`,`source_record_id`,`source_record_version`),
  KEY `idx_reference_recalc_ready` (`status`,`available_at`,`id`),
  CONSTRAINT `ck_reference_recalc_attempt` CHECK ((`attempt_count` between 0 and 20)),
  CONSTRAINT `ck_reference_recalc_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'RUNNING',_utf8mb4'PASSED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_reference_recalc_task`
--

LOCK TABLES `un_module_reference_recalc_task` WRITE;
/*!40000 ALTER TABLE `un_module_reference_recalc_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_reference_recalc_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_reference_state`
--

DROP TABLE IF EXISTS `un_module_reference_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_reference_state` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `field_snapshot_id` bigint NOT NULL,
  `source_record_id` bigint DEFAULT NULL,
  `source_record_version` bigint DEFAULT NULL,
  `recalculation_state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_correlation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reference_state_field` (`system_id`,`tenant_id`,`record_id`,`schema_version_id`,`module_snapshot_id`,`field_snapshot_id`),
  KEY `idx_reference_state_source` (`system_id`,`tenant_id`,`source_record_id`,`recalculation_state`),
  KEY `fk_reference_state_field` (`system_id`,`schema_version_id`,`module_snapshot_id`,`field_snapshot_id`),
  CONSTRAINT `fk_reference_state_field` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`) REFERENCES `un_module_runtime_schema_field` (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reference_state_record` FOREIGN KEY (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_reference_failure` CHECK ((((`recalculation_state` = _utf8mb4'FAILED') and (`failure_correlation_id` is not null)) or ((`recalculation_state` <> _utf8mb4'FAILED') and (`failure_correlation_id` is null)))),
  CONSTRAINT `ck_reference_state` CHECK ((`recalculation_state` in (_utf8mb4'READY',_utf8mb4'PENDING',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_reference_state`
--

LOCK TABLES `un_module_reference_state` WRITE;
/*!40000 ALTER TABLE `un_module_reference_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_reference_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report`
--

DROP TABLE IF EXISTS `un_module_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `report_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `report_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `draft_json` json NOT NULL,
  `draft_version` bigint NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `active_version_no` int unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_report_code` (`system_id`,`tenant_id`,`report_code`),
  UNIQUE KEY `uk_module_report_active_identity` (`system_id`,`tenant_id`,`id`,`active_version_id`,`active_version_no`),
  KEY `idx_module_report_list` (`system_id`,`tenant_id`,`updated_at` DESC,`id` DESC),
  KEY `idx_module_report_active` (`system_id`,`tenant_id`,`active_version_id`),
  CONSTRAINT `fk_module_report_active_version` FOREIGN KEY (`system_id`, `tenant_id`, `id`, `active_version_id`, `active_version_no`) REFERENCES `un_module_report_version` (`system_id`, `tenant_id`, `report_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_active` CHECK ((((`active_version_id` is null) and (`active_version_no` is null)) or ((`active_version_id` > 0) and (`active_version_no` > 0)))),
  CONSTRAINT `ck_module_report_code` CHECK (regexp_like(`report_code`,_ascii'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_module_report_description` CHECK (((`description` is null) or (char_length(trim(`description`)) between 1 and 2000))),
  CONSTRAINT `ck_module_report_draft` CHECK (((`draft_version` > 0) and (json_type(`draft_json`) = _utf8mb4'OBJECT') and (length(`draft_json`) between 2 and 262144))),
  CONSTRAINT `ck_module_report_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0))),
  CONSTRAINT `ck_module_report_name` CHECK ((char_length(trim(`report_name`)) between 1 and 200)),
  CONSTRAINT `ck_module_report_state` CHECK (((`updated_at` >= `created_at`) and (`version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report`
--

LOCK TABLES `un_module_report` WRITE;
/*!40000 ALTER TABLE `un_module_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_export_run`
--

DROP TABLE IF EXISTS `un_module_report_export_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_export_run` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `report_version_id` bigint NOT NULL,
  `report_version_no` int unsigned NOT NULL,
  `report_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `report_name` varchar(200) NOT NULL,
  `data_source_id` bigint NOT NULL,
  `data_source_version_id` bigint NOT NULL,
  `data_source_version_no` int unsigned NOT NULL,
  `data_source_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `data_source_name` varchar(200) NOT NULL,
  `module_id` bigint NOT NULL,
  `module_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schema_version_id` varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `fields_json` json NOT NULL,
  `fields_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `field_count` tinyint unsigned NOT NULL,
  `request_key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `requested_by_account_id` bigint NOT NULL,
  `requested_by_member_id` bigint NOT NULL,
  `job_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `total_rows` bigint DEFAULT NULL,
  `processed_rows` smallint unsigned NOT NULL,
  `truncated` tinyint(1) NOT NULL,
  `result_filename` varchar(180) DEFAULT NULL,
  `result_content` longblob,
  `result_size` bigint unsigned DEFAULT NULL,
  `error_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `request_id` varchar(128) NOT NULL,
  `trace_id` varchar(128) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_report_export_id` (`id`),
  UNIQUE KEY `uk_module_report_export_request` (`system_id`,`tenant_id`,`requested_by_member_id`,`report_id`,`request_key_hash`),
  UNIQUE KEY `uk_module_report_export_job` (`job_id`),
  KEY `idx_module_report_export_owner` (`system_id`,`tenant_id`,`report_id`,`requested_by_member_id`,`created_at` DESC,`id` DESC),
  KEY `idx_module_report_export_report_version` (`system_id`,`tenant_id`,`report_id`,`report_version_id`,`report_version_no`),
  KEY `idx_module_report_export_source_version` (`system_id`,`tenant_id`,`data_source_id`,`data_source_version_id`,`data_source_version_no`),
  KEY `idx_module_report_export_status` (`status`,`updated_at`,`id`),
  KEY `fk_module_report_export_requester` (`system_id`,`requested_by_member_id`),
  CONSTRAINT `fk_module_report_export_requester` FOREIGN KEY (`system_id`, `requested_by_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_export_root` FOREIGN KEY (`system_id`, `tenant_id`, `report_id`) REFERENCES `un_module_report` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_export_source` FOREIGN KEY (`system_id`, `tenant_id`, `data_source_id`, `data_source_version_id`, `data_source_version_no`) REFERENCES `un_module_data_source_version` (`system_id`, `tenant_id`, `data_source_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_export_version` FOREIGN KEY (`system_id`, `tenant_id`, `report_id`, `report_version_id`, `report_version_no`) REFERENCES `un_module_report_version` (`system_id`, `tenant_id`, `report_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_export_code` CHECK ((regexp_like(`report_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`data_source_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`module_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,99}$'))),
  CONSTRAINT `ck_module_report_export_fields` CHECK (((`field_count` between 1 and 100) and (json_type(`fields_json`) = _utf8mb4'ARRAY') and (json_length(`fields_json`) = `field_count`) and (length(`fields_json`) between 2 and 262144) and regexp_like(`fields_fingerprint`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_module_report_export_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`report_id` > 0) and (`report_version_id` > 0) and (`report_version_no` > 0) and (`data_source_id` > 0) and (`data_source_version_id` > 0) and (`data_source_version_no` > 0) and (`module_id` > 0) and (`requested_by_account_id` > 0) and (`requested_by_member_id` > 0) and (`job_id` > 0))),
  CONSTRAINT `ck_module_report_export_names` CHECK (((char_length(trim(`report_name`)) between 1 and 200) and (char_length(trim(`data_source_name`)) between 1 and 200) and (char_length(trim(`schema_version_id`)) between 1 and 200))),
  CONSTRAINT `ck_module_report_export_request` CHECK ((regexp_like(`request_key_hash`,_utf8mb4'^[0-9a-f]{64}$') and (char_length(trim(`request_id`)) between 1 and 128) and (char_length(trim(`trace_id`)) between 1 and 128))),
  CONSTRAINT `ck_module_report_export_result` CHECK ((((`total_rows` is null) or (`total_rows` >= 0)) and (`processed_rows` between 0 and 5000))),
  CONSTRAINT `ck_module_report_export_state` CHECK ((((`status` = _utf8mb4'QUEUED') and (`total_rows` is null) and (`processed_rows` = 0) and (`truncated` = 0) and (`result_filename` is null) and (`result_content` is null) and (`result_size` is null) and (`error_code` is null) and (`error_message` is null) and (`finished_at` is null) and ((`started_at` is null) or (`started_at` >= `created_at`))) or ((`status` = _utf8mb4'RUNNING') and (`total_rows` is null) and (`processed_rows` = 0) and (`truncated` = 0) and (`result_filename` is null) and (`result_content` is null) and (`result_size` is null) and (`error_code` is null) and (`error_message` is null) and (`started_at` >= `created_at`) and (`finished_at` is null)) or ((`status` = _utf8mb4'SUCCEEDED') and (`total_rows` >= `processed_rows`) and (`truncated` = (`total_rows` > `processed_rows`)) and (char_length(trim(`result_filename`)) between 6 and 180) and (lower(`result_filename`) like _utf8mb4'%.xlsx') and (`result_content` is not null) and (`result_size` > 0) and (`result_size` = length(`result_content`)) and (`result_size` <= 134217728) and (`error_code` is null) and (`error_message` is null) and (`started_at` >= `created_at`) and (`finished_at` >= `started_at`)) or ((`status` = _utf8mb4'FAILED') and (`total_rows` is null) and (`processed_rows` = 0) and (`truncated` = 0) and (`result_filename` is null) and (`result_content` is null) and (`result_size` is null) and regexp_like(`error_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,99}$') and (char_length(trim(`error_message`)) between 1 and 500) and (`finished_at` >= `created_at`) and ((`started_at` is null) or (`finished_at` >= `started_at`))))),
  CONSTRAINT `ck_module_report_export_time` CHECK (((`updated_at` >= `created_at`) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_export_run`
--

LOCK TABLES `un_module_report_export_run` WRITE;
/*!40000 ALTER TABLE `un_module_report_export_run` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_export_run` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_schedule`
--

DROP TABLE IF EXISTS `un_module_report_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_schedule` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `report_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schedule_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schedule_name` varchar(200) NOT NULL,
  `time_zone` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `cadence_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `local_time` time NOT NULL,
  `days_of_week_json` json NOT NULL,
  `recipient_count` tinyint unsigned NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `owner_account_id` bigint NOT NULL,
  `owner_member_id` bigint NOT NULL,
  `next_fire_at` datetime(6) DEFAULT NULL,
  `last_scheduled_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_report_schedule_id` (`id`),
  UNIQUE KEY `uk_module_report_schedule_code` (`system_id`,`tenant_id`,`report_id`,`schedule_code`),
  UNIQUE KEY `uk_module_report_schedule_report_identity` (`system_id`,`tenant_id`,`id`,`report_id`),
  KEY `idx_module_report_schedule_list` (`system_id`,`tenant_id`,`report_id`,`updated_at` DESC,`id` DESC),
  KEY `idx_module_report_schedule_due` (`enabled`,`next_fire_at`,`system_id`,`tenant_id`,`id`),
  KEY `fk_module_report_schedule_owner` (`system_id`,`owner_member_id`,`tenant_id`),
  CONSTRAINT `fk_module_report_schedule_owner` FOREIGN KEY (`system_id`, `owner_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_schedule_report` FOREIGN KEY (`system_id`, `tenant_id`, `report_id`) REFERENCES `un_module_report` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_schedule_cadence` CHECK ((json_schema_valid(_utf8mb4'{"type":"array","uniqueItems":true,"maxItems":7,"items":{"type":"integer","minimum":1,"maximum":7}}',`days_of_week_json`) and (((`cadence_type` = _utf8mb4'DAILY') and (json_length(`days_of_week_json`) = 0)) or ((`cadence_type` = _utf8mb4'WEEKLY') and (json_length(`days_of_week_json`) between 1 and 7))))),
  CONSTRAINT `ck_module_report_schedule_codes` CHECK ((regexp_like(`schedule_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`report_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$'))),
  CONSTRAINT `ck_module_report_schedule_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`report_id` > 0) and (`owner_account_id` > 0) and (`owner_member_id` > 0))),
  CONSTRAINT `ck_module_report_schedule_name` CHECK ((char_length(trim(`schedule_name`)) between 1 and 200)),
  CONSTRAINT `ck_module_report_schedule_recipients` CHECK ((`recipient_count` between 1 and 50)),
  CONSTRAINT `ck_module_report_schedule_state` CHECK (((`enabled` in (0,1)) and (`enabled` = (`next_fire_at` is not null)) and ((`last_scheduled_at` is null) or (`last_scheduled_at` <= `updated_at`)) and (`updated_at` >= `created_at`) and (`version` > 0))),
  CONSTRAINT `ck_module_report_schedule_zone` CHECK (((`time_zone` = _utf8mb4'UTC') or regexp_like(`time_zone`,_utf8mb4'^[A-Za-z][A-Za-z0-9._+-]*/[A-Za-z0-9._+-]+(/[A-Za-z0-9._+-]+)*$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_schedule`
--

LOCK TABLES `un_module_report_schedule` WRITE;
/*!40000 ALTER TABLE `un_module_report_schedule` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_schedule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_schedule_delivery`
--

DROP TABLE IF EXISTS `un_module_report_schedule_delivery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_schedule_delivery` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `occurrence_id` bigint NOT NULL,
  `recipient_member_id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `delivery_key` varchar(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `delivered_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_report_schedule_delivery_id` (`id`),
  UNIQUE KEY `uk_module_report_schedule_delivery_recipient` (`system_id`,`tenant_id`,`occurrence_id`,`recipient_member_id`),
  UNIQUE KEY `uk_module_report_schedule_delivery_key` (`system_id`,`tenant_id`,`delivery_key`),
  UNIQUE KEY `uk_module_report_schedule_delivery_message` (`system_id`,`tenant_id`,`message_id`),
  KEY `idx_module_report_schedule_delivery_history` (`system_id`,`tenant_id`,`recipient_member_id`,`delivered_at` DESC,`occurrence_id` DESC),
  KEY `fk_module_report_schedule_delivery_member` (`system_id`,`recipient_member_id`,`tenant_id`),
  CONSTRAINT `fk_module_report_schedule_delivery_member` FOREIGN KEY (`system_id`, `recipient_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_schedule_delivery_occurrence_recipient` FOREIGN KEY (`system_id`, `tenant_id`, `occurrence_id`, `recipient_member_id`) REFERENCES `un_module_report_schedule_occurrence_recipient` (`system_id`, `tenant_id`, `occurrence_id`, `recipient_member_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_schedule_delivery_identity` CHECK (((`id` > 0) and (`occurrence_id` > 0) and (`recipient_member_id` > 0) and (`message_id` > 0) and (char_length(trim(`delivery_key`)) between 1 and 256)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_schedule_delivery`
--

LOCK TABLES `un_module_report_schedule_delivery` WRITE;
/*!40000 ALTER TABLE `un_module_report_schedule_delivery` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_schedule_delivery` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_schedule_occurrence`
--

DROP TABLE IF EXISTS `un_module_report_schedule_occurrence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_schedule_occurrence` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `schedule_id` bigint NOT NULL,
  `schedule_version` bigint NOT NULL,
  `schedule_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schedule_name` varchar(200) NOT NULL,
  `report_id` bigint NOT NULL,
  `report_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_account_id` bigint NOT NULL,
  `owner_member_id` bigint NOT NULL,
  `configured_recipient_count` tinyint unsigned NOT NULL,
  `scheduled_at` datetime(6) NOT NULL,
  `occurrence_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attempt_count` tinyint unsigned NOT NULL,
  `max_attempts` tinyint unsigned NOT NULL,
  `export_id` bigint DEFAULT NULL,
  `export_status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_filename` varchar(180) DEFAULT NULL,
  `result_size` bigint DEFAULT NULL,
  `total_rows` bigint DEFAULT NULL,
  `processed_rows` int unsigned NOT NULL,
  `truncated` tinyint(1) NOT NULL,
  `delivered_recipient_count` tinyint unsigned NOT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `failure_message` varchar(500) DEFAULT NULL,
  `available_at` datetime(6) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_module_report_schedule_occurrence_id` (`id`),
  UNIQUE KEY `uk_module_report_schedule_occurrence_fire` (`system_id`,`tenant_id`,`schedule_id`,`scheduled_at`),
  UNIQUE KEY `uk_module_report_schedule_occurrence_key` (`system_id`,`tenant_id`,`occurrence_key`),
  UNIQUE KEY `uk_module_report_schedule_occurrence_identity` (`system_id`,`tenant_id`,`id`,`report_id`),
  UNIQUE KEY `uk_module_report_schedule_occurrence_export` (`system_id`,`tenant_id`,`export_id`),
  KEY `idx_module_report_schedule_occurrence_claim` (`status`,`available_at`,`lease_until`,`scheduled_at`,`system_id`,`tenant_id`,`id`),
  KEY `idx_module_report_schedule_occurrence_history` (`system_id`,`tenant_id`,`report_code`,`scheduled_at` DESC,`id` DESC),
  KEY `fk_module_report_schedule_occurrence_schedule` (`system_id`,`tenant_id`,`schedule_id`,`report_id`),
  KEY `fk_module_report_schedule_occurrence_owner` (`system_id`,`owner_member_id`,`tenant_id`),
  CONSTRAINT `fk_module_report_schedule_occurrence_export` FOREIGN KEY (`system_id`, `tenant_id`, `export_id`) REFERENCES `un_module_report_export_run` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_schedule_occurrence_owner` FOREIGN KEY (`system_id`, `owner_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_schedule_occurrence_schedule` FOREIGN KEY (`system_id`, `tenant_id`, `schedule_id`, `report_id`) REFERENCES `un_module_report_schedule` (`system_id`, `tenant_id`, `id`, `report_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_schedule_occurrence_attempt` CHECK (((`max_attempts` between 1 and 3) and (`attempt_count` between 0 and `max_attempts`))),
  CONSTRAINT `ck_module_report_schedule_occurrence_counts` CHECK (((`configured_recipient_count` between 1 and 50) and (`delivered_recipient_count` <= `configured_recipient_count`) and (`processed_rows` <= 5000) and ((`total_rows` is null) or (`total_rows` >= `processed_rows`)))),
  CONSTRAINT `ck_module_report_schedule_occurrence_identity` CHECK (((`id` > 0) and (`schedule_id` > 0) and (`schedule_version` > 0) and (`report_id` > 0) and (`owner_account_id` > 0) and (`owner_member_id` > 0))),
  CONSTRAINT `ck_module_report_schedule_occurrence_state` CHECK ((((`status` = _utf8mb4'PENDING') and (`attempt_count` < `max_attempts`) and (`export_id` is null) and (`export_status` is null) and (`result_filename` is null) and (`result_size` is null) and (`total_rows` is null) and (`processed_rows` = 0) and (`truncated` = 0) and (`delivered_recipient_count` = 0) and (`available_at` is not null) and (`lease_until` is null) and (`finished_at` is null)) or ((`status` = _utf8mb4'RUNNING') and (`attempt_count` between 1 and `max_attempts`) and (`result_filename` is null) and (`result_size` is null) and (`total_rows` is null) and (`processed_rows` = 0) and (`truncated` = 0) and (`delivered_recipient_count` = 0) and (`available_at` is not null) and (`finished_at` is null)) or ((`status` = _utf8mb4'SUCCEEDED') and (`attempt_count` between 1 and `max_attempts`) and (`export_id` > 0) and (`export_status` = _utf8mb4'SUCCEEDED') and (`result_filename` like _utf8mb4'%.xlsx') and (`result_size` > 0) and (`total_rows` >= `processed_rows`) and (`truncated` = (`total_rows` > `processed_rows`)) and (`failure_code` is null) and (`failure_message` is null) and (`available_at` is null) and (`lease_until` is null) and (`finished_at` is not null)) or ((`status` = _utf8mb4'FAILED') and (`attempt_count` between 1 and `max_attempts`) and (`result_filename` is null) and (`result_size` is null) and (`total_rows` is null) and (`processed_rows` = 0) and (`truncated` = 0) and regexp_like(`failure_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and (char_length(trim(`failure_message`)) between 1 and 500) and (`available_at` is null) and (`lease_until` is null) and (`finished_at` is not null)))),
  CONSTRAINT `ck_module_report_schedule_occurrence_time` CHECK ((((`lease_until` is null) or (`lease_until` > `updated_at`)) and ((`started_at` is null) or (`started_at` >= `created_at`)) and ((`finished_at` is null) or (`finished_at` >= `created_at`)) and (`updated_at` >= `created_at`) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_schedule_occurrence`
--

LOCK TABLES `un_module_report_schedule_occurrence` WRITE;
/*!40000 ALTER TABLE `un_module_report_schedule_occurrence` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_schedule_occurrence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_schedule_occurrence_recipient`
--

DROP TABLE IF EXISTS `un_module_report_schedule_occurrence_recipient`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_schedule_occurrence_recipient` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `occurrence_id` bigint NOT NULL,
  `recipient_ordinal` tinyint unsigned NOT NULL,
  `recipient_member_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`occurrence_id`,`recipient_ordinal`),
  UNIQUE KEY `uk_module_report_schedule_occurrence_recipient_member` (`system_id`,`tenant_id`,`occurrence_id`,`recipient_member_id`),
  KEY `fk_module_report_schedule_occurrence_recipient_member` (`system_id`,`recipient_member_id`,`tenant_id`),
  CONSTRAINT `fk_module_report_schedule_occurrence_recipient_member` FOREIGN KEY (`system_id`, `recipient_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_schedule_occurrence_recipient_root` FOREIGN KEY (`system_id`, `tenant_id`, `occurrence_id`) REFERENCES `un_module_report_schedule_occurrence` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_schedule_occurrence_recipient` CHECK (((`occurrence_id` > 0) and (`recipient_ordinal` < 50) and (`recipient_member_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_schedule_occurrence_recipient`
--

LOCK TABLES `un_module_report_schedule_occurrence_recipient` WRITE;
/*!40000 ALTER TABLE `un_module_report_schedule_occurrence_recipient` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_schedule_occurrence_recipient` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_schedule_recipient`
--

DROP TABLE IF EXISTS `un_module_report_schedule_recipient`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_schedule_recipient` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `schedule_id` bigint NOT NULL,
  `recipient_ordinal` tinyint unsigned NOT NULL,
  `recipient_member_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`schedule_id`,`recipient_ordinal`),
  UNIQUE KEY `uk_module_report_schedule_recipient_member` (`system_id`,`tenant_id`,`schedule_id`,`recipient_member_id`),
  KEY `idx_module_report_schedule_recipient_lookup` (`system_id`,`tenant_id`,`recipient_member_id`,`schedule_id`),
  KEY `fk_module_report_schedule_recipient_member` (`system_id`,`recipient_member_id`,`tenant_id`),
  CONSTRAINT `fk_module_report_schedule_recipient_member` FOREIGN KEY (`system_id`, `recipient_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_schedule_recipient_root` FOREIGN KEY (`system_id`, `tenant_id`, `schedule_id`) REFERENCES `un_module_report_schedule` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_schedule_recipient` CHECK (((`schedule_id` > 0) and (`recipient_ordinal` < 50) and (`recipient_member_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_schedule_recipient`
--

LOCK TABLES `un_module_report_schedule_recipient` WRITE;
/*!40000 ALTER TABLE `un_module_report_schedule_recipient` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_schedule_recipient` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_version`
--

DROP TABLE IF EXISTS `un_module_report_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_version` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `source_draft_version` bigint NOT NULL,
  `report_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `report_name` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `data_source_id` bigint NOT NULL,
  `data_source_version_id` bigint NOT NULL,
  `data_source_version_no` int unsigned NOT NULL,
  `data_source_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `data_source_name` varchar(200) NOT NULL,
  `module_id` bigint NOT NULL,
  `module_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schema_version_id` varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `field_count` tinyint unsigned NOT NULL,
  `snapshot_json` json NOT NULL,
  `snapshot_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `published_by_member_id` bigint NOT NULL,
  `published_at` datetime(6) NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`report_id`,`id`),
  UNIQUE KEY `uk_module_report_version_id` (`system_id`,`tenant_id`,`report_id`,`id`,`version_no`),
  UNIQUE KEY `uk_module_report_version_no` (`system_id`,`tenant_id`,`report_id`,`version_no`),
  KEY `idx_module_report_version_list` (`system_id`,`tenant_id`,`report_id`,`version_no` DESC),
  KEY `idx_module_report_version_fingerprint` (`system_id`,`tenant_id`,`report_id`,`snapshot_fingerprint`,`version_no` DESC),
  KEY `idx_module_report_version_source` (`system_id`,`tenant_id`,`data_source_id`,`data_source_version_id`,`data_source_version_no`),
  KEY `fk_module_report_version_publisher` (`system_id`,`published_by_member_id`),
  CONSTRAINT `fk_module_report_version_publisher` FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_version_root` FOREIGN KEY (`system_id`, `tenant_id`, `report_id`) REFERENCES `un_module_report` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_module_report_version_source` FOREIGN KEY (`system_id`, `tenant_id`, `data_source_id`, `data_source_version_id`, `data_source_version_no`) REFERENCES `un_module_data_source_version` (`system_id`, `tenant_id`, `data_source_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_version_code` CHECK ((regexp_like(`report_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`data_source_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$') and regexp_like(`module_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,99}$'))),
  CONSTRAINT `ck_module_report_version_fields` CHECK ((`field_count` between 1 and 100)),
  CONSTRAINT `ck_module_report_version_identity` CHECK (((`id` > 0) and (`report_id` > 0) and (`version_no` > 0) and (`source_draft_version` > 0) and (`data_source_id` > 0) and (`data_source_version_id` > 0) and (`data_source_version_no` > 0) and (`module_id` > 0) and (`published_by_member_id` > 0))),
  CONSTRAINT `ck_module_report_version_name` CHECK (((char_length(trim(`report_name`)) between 1 and 200) and (char_length(trim(`data_source_name`)) between 1 and 200) and ((`description` is null) or (char_length(trim(`description`)) between 1 and 2000)))),
  CONSTRAINT `ck_module_report_version_schema` CHECK ((char_length(trim(`schema_version_id`)) between 1 and 200)),
  CONSTRAINT `ck_module_report_version_snapshot` CHECK (((json_type(`snapshot_json`) = _utf8mb4'OBJECT') and (length(`snapshot_json`) between 2 and 262144) and regexp_like(`snapshot_fingerprint`,_utf8mb4'^[0-9a-f]{64}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_version`
--

LOCK TABLES `un_module_report_version` WRITE;
/*!40000 ALTER TABLE `un_module_report_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_report_version_field`
--

DROP TABLE IF EXISTS `un_module_report_version_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_report_version_field` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `report_version_id` bigint NOT NULL,
  `report_version_no` int unsigned NOT NULL,
  `field_ordinal` tinyint unsigned NOT NULL,
  `field_id` bigint NOT NULL,
  `field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `field_name` varchar(200) NOT NULL,
  `field_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `query_type` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`report_id`,`report_version_id`,`id`),
  UNIQUE KEY `uk_module_report_field_ordinal` (`system_id`,`tenant_id`,`report_id`,`report_version_id`,`field_ordinal`),
  UNIQUE KEY `uk_module_report_field_code` (`system_id`,`tenant_id`,`report_id`,`report_version_id`,`field_code`),
  UNIQUE KEY `uk_module_report_field_id` (`system_id`,`tenant_id`,`report_id`,`report_version_id`,`field_id`),
  KEY `idx_module_report_field_version` (`system_id`,`tenant_id`,`report_id`,`report_version_id`,`report_version_no`),
  CONSTRAINT `fk_module_report_field_version` FOREIGN KEY (`system_id`, `tenant_id`, `report_id`, `report_version_id`, `report_version_no`) REFERENCES `un_module_report_version` (`system_id`, `tenant_id`, `report_id`, `id`, `version_no`) ON DELETE RESTRICT,
  CONSTRAINT `ck_module_report_field_code` CHECK (regexp_like(`field_code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_module_report_field_identity` CHECK (((`id` > 0) and (`report_id` > 0) and (`report_version_id` > 0) and (`report_version_no` > 0) and (`field_ordinal` < 100) and (`field_id` > 0))),
  CONSTRAINT `ck_module_report_field_name` CHECK ((char_length(trim(`field_name`)) between 1 and 200)),
  CONSTRAINT `ck_module_report_field_type` CHECK ((regexp_like(`field_type`,_utf8mb4'^[A-Z][A-Z0-9_]{0,99}$') and regexp_like(`query_type`,_utf8mb4'^[A-Z][A-Z0-9_]{0,99}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_report_version_field`
--

LOCK TABLES `un_module_report_version_field` WRITE;
/*!40000 ALTER TABLE `un_module_report_version_field` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_report_version_field` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_rule`
--

DROP TABLE IF EXISTS `un_module_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_rule` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `module_id` bigint NOT NULL,
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `priority` int NOT NULL DEFAULT '100',
  `condition_json` json NOT NULL,
  `effect_json` json NOT NULL,
  `desired_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `code_locked_at` datetime(3) DEFAULT NULL,
  `created_revision` bigint NOT NULL,
  `updated_revision` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_rule_module_code` (`system_id`,`module_id`,`rule_code`),
  UNIQUE KEY `uk_module_rule_system_id` (`system_id`,`id`),
  KEY `idx_module_rule_list` (`system_id`,`module_id`,`desired_status`,`priority`,`id`),
  CONSTRAINT `fk_module_rule_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `un_module_definition` (`system_id`, `id`),
  CONSTRAINT `ck_module_rule_code` CHECK (regexp_like(`rule_code`,_utf8mb4'^[a-z][a-z0-9_]{1,63}$')),
  CONSTRAINT `ck_module_rule_delete` CHECK (((`deleted_at` is null) or (`deleted_by` is not null))),
  CONSTRAINT `ck_module_rule_priority` CHECK (((`priority` >= 0) and (`priority` <= 10000))),
  CONSTRAINT `ck_module_rule_revision` CHECK (((`created_revision` >= 0) and (`updated_revision` >= `created_revision`))),
  CONSTRAINT `ck_module_rule_status` CHECK ((`desired_status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_module_rule_type` CHECK ((`rule_type` in (_utf8mb4'FIELD_VISIBILITY',_utf8mb4'FIELD_REQUIRED',_utf8mb4'FIELD_READ_ONLY',_utf8mb4'ACTION_ENABLED',_utf8mb4'DELETE_ALLOWED',_utf8mb4'APPROVAL_REQUIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_rule`
--

LOCK TABLES `un_module_rule` WRITE;
/*!40000 ALTER TABLE `un_module_rule` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_runtime_schema_field`
--

DROP TABLE IF EXISTS `un_module_runtime_schema_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_runtime_schema_field` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `field_snapshot_id` bigint NOT NULL,
  `source_field_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `parent_field_snapshot_id` bigint DEFAULT NULL,
  `dictionary_id` bigint DEFAULT NULL,
  `target_module_id` bigint DEFAULT NULL,
  `field_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_scope` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_required` tinyint(1) NOT NULL,
  `is_readonly` tinyint(1) NOT NULL,
  `property_json` json NOT NULL,
  `result_schema` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evaluator_version` smallint unsigned DEFAULT NULL,
  `expression_checksum` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `topological_rank` smallint unsigned DEFAULT NULL,
  `dependency_json` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rsf_identity` (`system_id`,`schema_version_id`,`module_snapshot_id`,`field_snapshot_id`),
  UNIQUE KEY `uk_rsf_discriminator` (`system_id`,`schema_version_id`,`module_snapshot_id`,`field_snapshot_id`,`field_type`,`field_scope`),
  UNIQUE KEY `uk_rsf_logical` (`system_id`,`schema_version_id`,`module_snapshot_id`,`logical_field_id`),
  UNIQUE KEY `uk_rsf_result` (`system_id`,`schema_version_id`,`module_snapshot_id`,`field_snapshot_id`,`field_type`,`field_scope`,`result_schema`),
  KEY `idx_rsf_logical` (`system_id`,`logical_module_id`,`logical_field_id`,`schema_version_id`),
  KEY `fk_rsf_parent` (`system_id`,`schema_version_id`,`module_snapshot_id`,`parent_field_snapshot_id`),
  KEY `idx_rsf_derived_rank` (`system_id`,`schema_version_id`,`module_snapshot_id`,`topological_rank`,`field_snapshot_id`),
  CONSTRAINT `fk_rsf_module` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_runtime_schema_module` (`system_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_rsf_parent` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`, `parent_field_snapshot_id`) REFERENCES `un_module_runtime_schema_field` (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_rsf_column_type` CHECK (((`field_scope` <> _utf8mb4'SUBTABLE_COLUMN') or (`field_type` <> _utf8mb4'SUBTABLE'))),
  CONSTRAINT `ck_rsf_derived_contract` CHECK ((((`field_scope` = _utf8mb4'RECORD') and (`field_type` in (_utf8mb4'FORMULA',_utf8mb4'SUMMARY',_utf8mb4'CALCULATED',_utf8mb4'LOOKUP',_utf8mb4'AGGREGATE',_utf8mb4'AI_FILL')) and (`is_required` = false) and (`is_readonly` = true) and (`result_schema` in (_utf8mb4'STRING',_utf8mb4'DECIMAL',_utf8mb4'INTEGER',_utf8mb4'DATE',_utf8mb4'DATETIME',_utf8mb4'BOOLEAN')) and (`evaluator_version` = 1) and regexp_like(`expression_checksum`,_utf8mb4'^[a-f0-9]{64}$') and (`topological_rank` between 0 and 64) and (`dependency_json` is not null) and (json_type(`dependency_json`) = _utf8mb4'ARRAY') and (length(`dependency_json`) <= 32768)) or ((`field_type` not in (_utf8mb4'FORMULA',_utf8mb4'SUMMARY',_utf8mb4'CALCULATED',_utf8mb4'LOOKUP',_utf8mb4'AGGREGATE',_utf8mb4'AI_FILL')) and (`result_schema` is null) and (`evaluator_version` is null) and (`expression_checksum` is null) and (`topological_rank` is null) and (`dependency_json` is null)))),
  CONSTRAINT `ck_rsf_parent_scope` CHECK ((((`field_scope` = _utf8mb4'RECORD') and (`parent_field_snapshot_id` is null)) or ((`field_scope` = _utf8mb4'SUBTABLE_COLUMN') and (`parent_field_snapshot_id` is not null)))),
  CONSTRAINT `ck_rsf_scope` CHECK ((`field_scope` in (_utf8mb4'RECORD',_utf8mb4'SUBTABLE_COLUMN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_runtime_schema_field`
--

LOCK TABLES `un_module_runtime_schema_field` WRITE;
/*!40000 ALTER TABLE `un_module_runtime_schema_field` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_runtime_schema_field` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_runtime_schema_module`
--

DROP TABLE IF EXISTS `un_module_runtime_schema_module`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_runtime_schema_module` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `source_module_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `module_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `snapshot_json` json NOT NULL,
  `checksum` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rsm_identity` (`system_id`,`schema_version_id`,`module_snapshot_id`),
  UNIQUE KEY `uk_rsm_source` (`system_id`,`schema_version_id`,`source_module_id`),
  KEY `idx_rsm_logical` (`system_id`,`logical_module_id`,`schema_version_id`),
  CONSTRAINT `fk_rsm_config_version` FOREIGN KEY (`system_id`, `schema_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_rsm_checksum` CHECK (regexp_like(`checksum`,_utf8mb4'^[a-f0-9]{64}$')),
  CONSTRAINT `ck_rsm_snapshot_size` CHECK ((length(`snapshot_json`) <= 262144))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_runtime_schema_module`
--

LOCK TABLES `un_module_runtime_schema_module` WRITE;
/*!40000 ALTER TABLE `un_module_runtime_schema_module` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_runtime_schema_module` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_saved_view`
--

DROP TABLE IF EXISTS `un_module_saved_view`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_saved_view` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `query_json` json NOT NULL,
  `columns_json` json NOT NULL,
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`deleted_at` is null) then 1 else NULL end)) STORED,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saved_view_name` (`system_id`,`tenant_id`,`member_id`,`logical_module_id`,`name`,`active_marker`),
  KEY `idx_saved_view_member` (`system_id`,`tenant_id`,`member_id`,`logical_module_id`,`updated_at`,`id`),
  KEY `fk_saved_view_member` (`system_id`,`member_id`),
  KEY `fk_saved_view_schema` (`system_id`,`schema_version_id`),
  KEY `fk_saved_view_module` (`system_id`,`module_snapshot_id`),
  CONSTRAINT `fk_saved_view_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_saved_view_module` FOREIGN KEY (`system_id`, `module_snapshot_id`) REFERENCES `un_module_definition` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_saved_view_schema` FOREIGN KEY (`system_id`, `schema_version_id`) REFERENCES `un_module_config_version` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_saved_view_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_saved_view_delete` CHECK ((((`deleted_at` is null) and (`deleted_by` is null)) or ((`deleted_at` is not null) and (`deleted_by` is not null)))),
  CONSTRAINT `ck_saved_view_module_identity` CHECK ((`logical_module_id` = `module_snapshot_id`)),
  CONSTRAINT `ck_saved_view_name` CHECK ((char_length(trim(`name`)) between 1 and 100)),
  CONSTRAINT `ck_saved_view_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_saved_view`
--

LOCK TABLES `un_module_saved_view` WRITE;
/*!40000 ALTER TABLE `un_module_saved_view` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_saved_view` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_sub_record`
--

DROP TABLE IF EXISTS `un_module_sub_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_sub_record` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `parent_record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `logical_module_id` bigint NOT NULL,
  `parent_field_snapshot_id` bigint NOT NULL,
  `parent_logical_field_id` bigint NOT NULL,
  `parent_field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUBTABLE',
  `parent_field_scope` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RECORD',
  `row_id` bigint NOT NULL,
  `client_row_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ordinal` int NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_record_identity` (`system_id`,`tenant_id`,`parent_record_id`,`schema_version_id`,`module_snapshot_id`,`parent_field_snapshot_id`,`row_id`),
  UNIQUE KEY `uk_sub_record_ordinal` (`system_id`,`tenant_id`,`parent_record_id`,`parent_field_snapshot_id`,`ordinal`),
  UNIQUE KEY `uk_sub_record_client` (`system_id`,`tenant_id`,`parent_record_id`,`parent_field_snapshot_id`,`client_row_key`),
  KEY `idx_sub_parent` (`system_id`,`tenant_id`,`parent_record_id`,`parent_field_snapshot_id`,`status`),
  KEY `fk_sub_parent_field` (`system_id`,`schema_version_id`,`module_snapshot_id`,`parent_field_snapshot_id`,`parent_field_type`,`parent_field_scope`),
  CONSTRAINT `fk_sub_parent_field` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`, `parent_field_snapshot_id`, `parent_field_type`, `parent_field_scope`) REFERENCES `un_module_runtime_schema_field` (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`, `field_type`, `field_scope`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sub_parent_record` FOREIGN KEY (`system_id`, `tenant_id`, `parent_record_id`, `schema_version_id`, `module_snapshot_id`) REFERENCES `un_module_record` (`system_id`, `tenant_id`, `record_id`, `schema_version_id`, `module_snapshot_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sub_ordinal` CHECK ((`ordinal` between 0 and 199)),
  CONSTRAINT `ck_sub_parent` CHECK (((`parent_field_type` = _utf8mb4'SUBTABLE') and (`parent_field_scope` = _utf8mb4'RECORD'))),
  CONSTRAINT `ck_sub_record_identity` CHECK ((`id` = `row_id`)),
  CONSTRAINT `ck_sub_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REMOVED'))),
  CONSTRAINT `ck_sub_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_sub_record`
--

LOCK TABLES `un_module_sub_record` WRITE;
/*!40000 ALTER TABLE `un_module_sub_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_sub_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_module_sub_value`
--

DROP TABLE IF EXISTS `un_module_sub_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_module_sub_value` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `parent_record_id` bigint NOT NULL,
  `schema_version_id` bigint NOT NULL,
  `module_snapshot_id` bigint NOT NULL,
  `parent_field_snapshot_id` bigint NOT NULL,
  `row_id` bigint NOT NULL,
  `column_field_snapshot_id` bigint NOT NULL,
  `source_field_id` bigint NOT NULL,
  `logical_field_id` bigint NOT NULL,
  `column_field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `column_field_scope` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUBTABLE_COLUMN',
  `ordinal` int NOT NULL DEFAULT '0',
  `string_value` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `decimal_value` decimal(38,10) DEFAULT NULL,
  `date_value` date DEFAULT NULL,
  `datetime_value` datetime(3) DEFAULT NULL,
  `time_value` time DEFAULT NULL,
  `boolean_value` tinyint(1) DEFAULT NULL,
  `currency_code` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_value` bigint DEFAULT NULL,
  `encrypted_value` blob,
  `encryption_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `value_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `hash_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `display_value` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_value_ordinal` (`system_id`,`tenant_id`,`parent_record_id`,`parent_field_snapshot_id`,`row_id`,`column_field_snapshot_id`,`ordinal`),
  KEY `idx_sv_logical` (`system_id`,`tenant_id`,`parent_record_id`,`logical_field_id`),
  KEY `fk_sv_sub_record` (`system_id`,`tenant_id`,`parent_record_id`,`schema_version_id`,`module_snapshot_id`,`parent_field_snapshot_id`,`row_id`),
  KEY `fk_sv_column_field` (`system_id`,`schema_version_id`,`module_snapshot_id`,`column_field_snapshot_id`,`column_field_type`,`column_field_scope`),
  CONSTRAINT `fk_sv_column_field` FOREIGN KEY (`system_id`, `schema_version_id`, `module_snapshot_id`, `column_field_snapshot_id`, `column_field_type`, `column_field_scope`) REFERENCES `un_module_runtime_schema_field` (`system_id`, `schema_version_id`, `module_snapshot_id`, `field_snapshot_id`, `field_type`, `field_scope`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sv_sub_record` FOREIGN KEY (`system_id`, `tenant_id`, `parent_record_id`, `schema_version_id`, `module_snapshot_id`, `parent_field_snapshot_id`, `row_id`) REFERENCES `un_module_sub_record` (`system_id`, `tenant_id`, `parent_record_id`, `schema_version_id`, `module_snapshot_id`, `parent_field_snapshot_id`, `row_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sv_currency` CHECK (((`currency_code` is null) or regexp_like(`currency_code`,_utf8mb4'^[A-Z]{3}$'))),
  CONSTRAINT `ck_sv_ordinal` CHECK ((`ordinal` between 0 and 99)),
  CONSTRAINT `ck_sv_scope` CHECK ((`column_field_scope` = _utf8mb4'SUBTABLE_COLUMN')),
  CONSTRAINT `ck_sv_sensitive` CHECK ((((`column_field_type` in (_utf8mb4'IDENTITY',_utf8mb4'SECRET')) and (`encrypted_value` is not null) and (`value_hash` is not null) and (`encryption_key_version` is not null) and (`hash_key_version` is not null)) or ((`column_field_type` not in (_utf8mb4'IDENTITY',_utf8mb4'SECRET')) and (`encrypted_value` is null) and (`value_hash` is null) and (`encryption_key_version` is null) and (`hash_key_version` is null)))),
  CONSTRAINT `ck_sv_typed_payload` CHECK (((((`column_field_type` in (_utf8mb4'TEXT',_utf8mb4'TAG',_utf8mb4'PHONE',_utf8mb4'EMAIL',_utf8mb4'URL')) and (`string_value` is not null)) or ((`column_field_type` in (_utf8mb4'TEXTAREA',_utf8mb4'ADDRESS',_utf8mb4'GEO',_utf8mb4'BARCODE',_utf8mb4'RICH_TEXT',_utf8mb4'JSON')) and (`text_value` is not null)) or ((`column_field_type` in (_utf8mb4'NUMBER',_utf8mb4'PERCENT',_utf8mb4'RATING',_utf8mb4'PROGRESS')) and (`decimal_value` is not null)) or ((`column_field_type` = _utf8mb4'MONEY') and (`decimal_value` is not null) and (`currency_code` is not null)) or ((`column_field_type` in (_utf8mb4'DATE',_utf8mb4'DATE_RANGE')) and (`date_value` is not null)) or ((`column_field_type` = _utf8mb4'DATETIME') and (`datetime_value` is not null)) or ((`column_field_type` in (_utf8mb4'TIME',_utf8mb4'TIME_RANGE')) and (`time_value` is not null)) or ((`column_field_type` = _utf8mb4'SWITCH') and (`boolean_value` is not null)) or ((`column_field_type` in (_utf8mb4'RADIO',_utf8mb4'MEMBER',_utf8mb4'DEPARTMENT',_utf8mb4'MULTI_SELECT',_utf8mb4'CASCADE',_utf8mb4'STATUS')) and (`reference_value` is not null)) or (`column_field_type` in (_utf8mb4'IDENTITY',_utf8mb4'SECRET'))) and ((((((((((`string_value` is not null) + (`text_value` is not null)) + (`decimal_value` is not null)) + (`date_value` is not null)) + (`datetime_value` is not null)) + (`time_value` is not null)) + (`boolean_value` is not null)) + (`reference_value` is not null)) + (`encrypted_value` is not null)) = 1))),
  CONSTRAINT `ck_sv_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_module_sub_value`
--

LOCK TABLES `un_module_sub_value` WRITE;
/*!40000 ALTER TABLE `un_module_sub_value` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_module_sub_value` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_application`
--

DROP TABLE IF EXISTS `un_openapi_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_application` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `service_member_id` bigint NOT NULL,
  `app_key` varchar(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(160) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scopes_json` json NOT NULL,
  `ip_allowlist_json` json NOT NULL,
  `rate_limit_per_minute` int unsigned NOT NULL,
  `current_credential_version` int unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_application_app_key` (`app_key`),
  UNIQUE KEY `uk_openapi_application_scope_id` (`system_id`,`tenant_id`,`id`),
  KEY `idx_openapi_application_tenant_status` (`system_id`,`tenant_id`,`status`,`updated_at`,`id`),
  KEY `idx_openapi_application_service_member` (`system_id`,`service_member_id`,`tenant_id`,`status`),
  CONSTRAINT `fk_openapi_application_member` FOREIGN KEY (`system_id`, `service_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_openapi_application_member_tenant` FOREIGN KEY (`system_id`, `service_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_openapi_application_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_openapi_application_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_application_app_key` CHECK (((char_length(`app_key`) between 16 and 96) and regexp_like(`app_key`,_utf8mb4'^[A-Za-z0-9_-]+$'))),
  CONSTRAINT `ck_openapi_application_credential_version` CHECK ((`current_credential_version` between 1 and 2147483647)),
  CONSTRAINT `ck_openapi_application_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`service_member_id` > 0) and (`created_by` > 0) and (`updated_by` > 0))),
  CONSTRAINT `ck_openapi_application_ip_allowlist` CHECK (((json_type(`ip_allowlist_json`) = _utf8mb4'ARRAY') and (json_length(`ip_allowlist_json`) <= 128))),
  CONSTRAINT `ck_openapi_application_name` CHECK ((char_length(trim(`name`)) between 1 and 160)),
  CONSTRAINT `ck_openapi_application_rate_limit` CHECK ((`rate_limit_per_minute` between 1 and 60000)),
  CONSTRAINT `ck_openapi_application_scopes` CHECK (((json_type(`scopes_json`) = _utf8mb4'ARRAY') and (json_length(`scopes_json`) between 1 and 64))),
  CONSTRAINT `ck_openapi_application_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_openapi_application_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_application`
--

LOCK TABLES `un_openapi_application` WRITE;
/*!40000 ALTER TABLE `un_openapi_application` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_call_log`
--

DROP TABLE IF EXISTS `un_openapi_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_call_log` (
  `id` bigint NOT NULL,
  `application_id` bigint DEFAULT NULL,
  `app_key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `credential_version` int unsigned DEFAULT NULL,
  `route_template` varchar(255) NOT NULL,
  `request_method` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_category` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `http_status` smallint unsigned NOT NULL,
  `latency_ms` bigint unsigned NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `observed_ip` varbinary(16) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_openapi_call_application` (`application_id`,`created_at`,`id`),
  KEY `idx_openapi_call_app_key` (`app_key_hash`,`created_at`,`id`),
  KEY `idx_openapi_call_result` (`result_category`,`created_at`,`id`),
  KEY `idx_openapi_call_request` (`request_id`),
  KEY `idx_openapi_call_trace` (`trace_id`,`created_at`),
  CONSTRAINT `fk_openapi_call_application` FOREIGN KEY (`application_id`) REFERENCES `un_openapi_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_call_correlation` CHECK (((char_length(`request_id`) between 1 and 64) and (char_length(`trace_id`) between 1 and 64))),
  CONSTRAINT `ck_openapi_call_identity` CHECK (((`id` > 0) and (char_length(`app_key_hash`) = 64) and regexp_like(`app_key_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`credential_version` is null) or (`credential_version` between 1 and 2147483647)))),
  CONSTRAINT `ck_openapi_call_ip` CHECK ((length(`observed_ip`) in (4,16))),
  CONSTRAINT `ck_openapi_call_method` CHECK ((`request_method` in (_utf8mb4'GET',_utf8mb4'HEAD',_utf8mb4'POST',_utf8mb4'PUT',_utf8mb4'PATCH',_utf8mb4'DELETE',_utf8mb4'OPTIONS'))),
  CONSTRAINT `ck_openapi_call_metrics` CHECK (((`http_status` between 100 and 599) and (`latency_ms` <= 86400000))),
  CONSTRAINT `ck_openapi_call_result` CHECK (((char_length(`result_category`) between 2 and 32) and regexp_like(`result_category`,_utf8mb4'^[A-Z][A-Z0-9_]+$'))),
  CONSTRAINT `ck_openapi_call_route` CHECK (((char_length(`route_template`) between 1 and 255) and (left(`route_template`,1) = _utf8mb4'/') and (locate(_utf8mb4'?',`route_template`) = 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_call_log`
--

LOCK TABLES `un_openapi_call_log` WRITE;
/*!40000 ALTER TABLE `un_openapi_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_call_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_callback_attempt`
--

DROP TABLE IF EXISTS `un_openapi_callback_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_callback_attempt` (
  `id` bigint NOT NULL,
  `delivery_id` bigint NOT NULL,
  `attempt_no` int unsigned NOT NULL,
  `outcome` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `http_status` smallint unsigned DEFAULT NULL,
  `duration_ms` bigint unsigned NOT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `started_at` datetime(3) NOT NULL,
  `completed_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_callback_attempt` (`delivery_id`,`attempt_no`),
  KEY `idx_openapi_callback_attempt_delivery` (`delivery_id`,`completed_at`,`id`),
  CONSTRAINT `fk_openapi_callback_attempt_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `un_openapi_callback_delivery` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_callback_attempt_duration` CHECK ((`duration_ms` <= 86400000)),
  CONSTRAINT `ck_openapi_callback_attempt_failure` CHECK (((`failure_code` is null) or regexp_like(`failure_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_openapi_callback_attempt_http` CHECK (((`http_status` is null) or (`http_status` between 100 and 599))),
  CONSTRAINT `ck_openapi_callback_attempt_identity` CHECK (((`id` > 0) and (`delivery_id` > 0) and (`attempt_no` between 1 and 10))),
  CONSTRAINT `ck_openapi_callback_attempt_outcome` CHECK ((`outcome` in (_utf8mb4'SUCCEEDED',_utf8mb4'RETRYABLE_FAILURE',_utf8mb4'TERMINAL_FAILURE'))),
  CONSTRAINT `ck_openapi_callback_attempt_time` CHECK ((`completed_at` >= `started_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_callback_attempt`
--

LOCK TABLES `un_openapi_callback_attempt` WRITE;
/*!40000 ALTER TABLE `un_openapi_callback_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_callback_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_callback_delivery`
--

DROP TABLE IF EXISTS `un_openapi_callback_delivery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_callback_delivery` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `application_id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `callback_version_id` bigint NOT NULL,
  `event_id` varchar(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload_json` json NOT NULL,
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attempt_count` int unsigned NOT NULL,
  `last_http_status` smallint unsigned DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_callback_delivery_dedupe` (`subscription_id`,`event_id`,`event_type`),
  KEY `idx_openapi_callback_delivery_scope` (`system_id`,`tenant_id`,`application_id`,`subscription_id`,`created_at`,`id`),
  KEY `idx_openapi_callback_delivery_status` (`status`,`updated_at`,`id`),
  KEY `idx_openapi_callback_delivery_request` (`request_id`),
  KEY `idx_openapi_callback_delivery_trace` (`trace_id`,`created_at`),
  KEY `fk_openapi_callback_delivery_version` (`callback_version_id`),
  CONSTRAINT `fk_openapi_callback_delivery_subscription` FOREIGN KEY (`system_id`, `tenant_id`, `application_id`, `subscription_id`) REFERENCES `un_openapi_callback_subscription` (`system_id`, `tenant_id`, `application_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_openapi_callback_delivery_version` FOREIGN KEY (`callback_version_id`) REFERENCES `un_openapi_callback_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_callback_delivery_attempt` CHECK ((`attempt_count` between 0 and 10)),
  CONSTRAINT `ck_openapi_callback_delivery_completion` CHECK ((((`status` in (_utf8mb4'PENDING',_utf8mb4'RETRYING')) and (`completed_at` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')) and (`completed_at` is not null)))),
  CONSTRAINT `ck_openapi_callback_delivery_event` CHECK (((char_length(`event_id`) between 1 and 160) and regexp_like(`event_type`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_openapi_callback_delivery_failure` CHECK (((`failure_code` is null) or regexp_like(`failure_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_openapi_callback_delivery_http` CHECK (((`last_http_status` is null) or (`last_http_status` between 100 and 599))),
  CONSTRAINT `ck_openapi_callback_delivery_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`application_id` > 0) and (`subscription_id` > 0) and (`callback_version_id` > 0))),
  CONSTRAINT `ck_openapi_callback_delivery_payload` CHECK (((json_type(`payload_json`) = _utf8mb4'OBJECT') and regexp_like(`payload_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_openapi_callback_delivery_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'RETRYING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED'))),
  CONSTRAINT `ck_openapi_callback_delivery_version_value` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_callback_delivery`
--

LOCK TABLES `un_openapi_callback_delivery` WRITE;
/*!40000 ALTER TABLE `un_openapi_callback_delivery` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_callback_delivery` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_callback_subscription`
--

DROP TABLE IF EXISTS `un_openapi_callback_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_callback_subscription` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `application_id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `current_config_version` int unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_callback_scope_id` (`system_id`,`tenant_id`,`application_id`,`id`),
  KEY `idx_openapi_callback_application` (`system_id`,`tenant_id`,`application_id`,`status`,`updated_at`,`id`),
  CONSTRAINT `fk_openapi_callback_application` FOREIGN KEY (`system_id`, `tenant_id`, `application_id`) REFERENCES `un_openapi_application` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_callback_config_version` CHECK ((`current_config_version` between 1 and 2147483647)),
  CONSTRAINT `ck_openapi_callback_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`application_id` > 0) and (`created_by` > 0) and (`updated_by` > 0))),
  CONSTRAINT `ck_openapi_callback_name` CHECK ((char_length(trim(`name`)) between 1 and 160)),
  CONSTRAINT `ck_openapi_callback_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_openapi_callback_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_callback_subscription`
--

LOCK TABLES `un_openapi_callback_subscription` WRITE;
/*!40000 ALTER TABLE `un_openapi_callback_subscription` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_callback_subscription` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_callback_version`
--

DROP TABLE IF EXISTS `un_openapi_callback_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_callback_version` (
  `id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `config_version` int unsigned NOT NULL,
  `endpoint_url` varchar(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_types_json` json NOT NULL,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `signing_secret_version` int unsigned NOT NULL,
  `max_attempts` int unsigned NOT NULL,
  `base_backoff_seconds` int unsigned NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `activated_at` datetime(3) NOT NULL,
  `retired_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_callback_version` (`subscription_id`,`config_version`),
  KEY `idx_openapi_callback_version_status` (`subscription_id`,`status`,`config_version`),
  CONSTRAINT `fk_openapi_callback_version_subscription` FOREIGN KEY (`subscription_id`) REFERENCES `un_openapi_callback_subscription` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_callback_endpoint` CHECK (((char_length(`endpoint_url`) between 9 and 1024) and regexp_like(`endpoint_url`,_utf8mb4'^https://') and (locate(_utf8mb4'@',`endpoint_url`) = 0) and (locate(_utf8mb4'?',`endpoint_url`) = 0) and (locate(_utf8mb4'#',`endpoint_url`) = 0))),
  CONSTRAINT `ck_openapi_callback_events` CHECK (((json_type(`event_types_json`) = _utf8mb4'ARRAY') and (json_length(`event_types_json`) between 1 and 32))),
  CONSTRAINT `ck_openapi_callback_retry` CHECK (((`max_attempts` between 1 and 10) and (`base_backoff_seconds` between 1 and 3600))),
  CONSTRAINT `ck_openapi_callback_secret_ref` CHECK (((char_length(`secret_ref`) between 3 and 512) and (`secret_ref` = trim(`secret_ref`)) and regexp_like(`secret_ref`,_utf8mb4'^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$'))),
  CONSTRAINT `ck_openapi_callback_version_identity` CHECK (((`id` > 0) and (`subscription_id` > 0) and (`created_by` > 0) and (`config_version` between 1 and 2147483647) and (`signing_secret_version` between 1 and 2147483647))),
  CONSTRAINT `ck_openapi_callback_version_lifecycle` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`retired_at` is null)) or ((`status` = _utf8mb4'RETIRED') and (`retired_at` is not null) and (`retired_at` >= `activated_at`)))),
  CONSTRAINT `ck_openapi_callback_version_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'RETIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_callback_version`
--

LOCK TABLES `un_openapi_callback_version` WRITE;
/*!40000 ALTER TABLE `un_openapi_callback_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_callback_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_credential`
--

DROP TABLE IF EXISTS `un_openapi_credential`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_credential` (
  `id` bigint NOT NULL,
  `application_id` bigint NOT NULL,
  `credential_version` int unsigned NOT NULL,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `activated_at` datetime(3) NOT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_credential_application_version` (`application_id`,`credential_version`),
  KEY `idx_openapi_credential_application_status` (`application_id`,`status`,`credential_version`),
  CONSTRAINT `fk_openapi_credential_application` FOREIGN KEY (`application_id`) REFERENCES `un_openapi_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_credential_identity` CHECK (((`id` > 0) and (`application_id` > 0) and (`created_by` > 0))),
  CONSTRAINT `ck_openapi_credential_lifecycle` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`revoked_at` is null)) or ((`status` = _utf8mb4'REVOKED') and (`revoked_at` is not null) and (`revoked_at` >= `activated_at`)))),
  CONSTRAINT `ck_openapi_credential_secret_ref` CHECK (((char_length(`secret_ref`) between 3 and 512) and (`secret_ref` = trim(`secret_ref`)) and regexp_like(`secret_ref`,_utf8mb4'^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$'))),
  CONSTRAINT `ck_openapi_credential_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REVOKED'))),
  CONSTRAINT `ck_openapi_credential_version` CHECK ((`credential_version` between 1 and 2147483647))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_credential`
--

LOCK TABLES `un_openapi_credential` WRITE;
/*!40000 ALTER TABLE `un_openapi_credential` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_credential` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_nonce`
--

DROP TABLE IF EXISTS `un_openapi_nonce`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_nonce` (
  `application_id` bigint NOT NULL,
  `credential_version` int unsigned NOT NULL,
  `nonce` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`application_id`,`credential_version`,`nonce`),
  KEY `idx_openapi_nonce_expiry` (`expires_at`,`application_id`),
  CONSTRAINT `fk_openapi_nonce_credential` FOREIGN KEY (`application_id`, `credential_version`) REFERENCES `un_openapi_credential` (`application_id`, `credential_version`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_nonce_expiry` CHECK ((`expires_at` > `created_at`)),
  CONSTRAINT `ck_openapi_nonce_value` CHECK (((char_length(`nonce`) between 16 and 128) and regexp_like(`nonce`,_utf8mb4'^[A-Za-z0-9._~-]+$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_nonce`
--

LOCK TABLES `un_openapi_nonce` WRITE;
/*!40000 ALTER TABLE `un_openapi_nonce` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_nonce` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_openapi_rate_bucket`
--

DROP TABLE IF EXISTS `un_openapi_rate_bucket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_openapi_rate_bucket` (
  `application_id` bigint NOT NULL,
  `window_start` datetime(3) NOT NULL,
  `request_count` int unsigned NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`application_id`,`window_start`),
  KEY `idx_openapi_rate_bucket_window` (`window_start`,`application_id`),
  CONSTRAINT `fk_openapi_rate_bucket_application` FOREIGN KEY (`application_id`) REFERENCES `un_openapi_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_openapi_rate_bucket_count` CHECK ((`request_count` between 0 and 4294967295)),
  CONSTRAINT `ck_openapi_rate_bucket_version` CHECK ((`version` >= 0)),
  CONSTRAINT `ck_openapi_rate_bucket_window` CHECK (((second(`window_start`) = 0) and (microsecond(`window_start`) = 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_openapi_rate_bucket`
--

LOCK TABLES `un_openapi_rate_bucket` WRITE;
/*!40000 ALTER TABLE `un_openapi_rate_bucket` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_openapi_rate_bucket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_access_request`
--

DROP TABLE IF EXISTS `un_plat_access_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_access_request` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `target_tenant_id` bigint DEFAULT NULL,
  `target_tenant_key` bigint GENERATED ALWAYS AS (coalesce(`target_tenant_id`,0)) STORED,
  `requested_role_id` bigint DEFAULT NULL,
  `request_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `active_request_marker` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'SUBMITTED') then 1 else NULL end)) STORED,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `decision_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_member_id` bigint DEFAULT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_access_request_active` (`account_id`,`system_id`,`target_tenant_key`,`active_request_marker`),
  KEY `idx_plat_access_request_review` (`system_id`,`status`,`created_at`),
  KEY `idx_plat_access_request_account` (`account_id`,`status`,`updated_at`),
  KEY `fk_plat_access_request_tenant` (`system_id`,`target_tenant_id`),
  KEY `fk_plat_access_request_role` (`system_id`,`requested_role_id`),
  KEY `fk_plat_access_request_member` (`system_id`,`resolved_member_id`),
  CONSTRAINT `fk_plat_access_request_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_access_request_member` FOREIGN KEY (`system_id`, `resolved_member_id`) REFERENCES `un_plat_member` (`system_id`, `id`),
  CONSTRAINT `fk_plat_access_request_role` FOREIGN KEY (`system_id`, `requested_role_id`) REFERENCES `un_plat_role` (`system_id`, `id`),
  CONSTRAINT `fk_plat_access_request_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_access_request_tenant` FOREIGN KEY (`system_id`, `target_tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_access_request_expiry` CHECK (((`expires_at` is null) or (`expires_at` > `created_at`))),
  CONSTRAINT `ck_plat_access_request_resolution` CHECK ((((`status` = _utf8mb4'APPROVED') and (`resolved_member_id` is not null)) or ((`status` <> _utf8mb4'APPROVED') and (`resolved_member_id` is null)))),
  CONSTRAINT `ck_plat_access_request_review` CHECK ((((`status` = _utf8mb4'SUBMITTED') and (`reviewed_at` is null) and (`reviewed_by` is null)) or ((`status` in (_utf8mb4'APPROVED',_utf8mb4'REJECTED')) and (`reviewed_at` is not null) and (`reviewed_by` is not null)) or (`status` in (_utf8mb4'CANCELLED',_utf8mb4'EXPIRED')))),
  CONSTRAINT `ck_plat_access_request_status` CHECK ((`status` in (_utf8mb4'SUBMITTED',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_access_request`
--

LOCK TABLES `un_plat_access_request` WRITE;
/*!40000 ALTER TABLE `un_plat_access_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_access_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_account`
--

DROP TABLE IF EXISTS `un_plat_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_account` (
  `id` bigint NOT NULL,
  `account_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `username_normalized` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email_normalized` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `locale` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN',
  `time_zone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Asia/Shanghai',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_code` (`account_code`),
  UNIQUE KEY `uk_plat_account_username` (`username_normalized`),
  UNIQUE KEY `uk_plat_account_email` (`email_normalized`),
  KEY `idx_plat_account_status` (`status`,`created_at`),
  CONSTRAINT `ck_plat_account_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'LOCKED',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_account`
--

LOCK TABLES `un_plat_account` WRITE;
/*!40000 ALTER TABLE `un_plat_account` DISABLE KEYS */;
INSERT INTO `un_plat_account` VALUES (2085917350597300225,'ACC_2085917350597300225','examine_root','examine_root',NULL,NULL,NULL,'Examine2 Root','zh-CN','Asia/Shanghai','ACTIVE','2026-08-08 03:25:05.722','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 03:25:05.722',2085917350597300225,NULL,NULL,6);
/*!40000 ALTER TABLE `un_plat_account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_account_role`
--

DROP TABLE IF EXISTS `un_plat_account_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_account_role` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLATFORM',
  `scope_key` bigint NOT NULL DEFAULT '0',
  `account_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `valid_from` datetime(3) NOT NULL,
  `valid_until` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_role` (`account_id`,`role_id`),
  KEY `idx_plat_account_role_valid` (`account_id`,`valid_from`,`valid_until`),
  KEY `fk_plat_account_role_scoped_role` (`scope_type`,`scope_key`,`role_id`),
  CONSTRAINT `fk_plat_account_role_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_account_role_scoped_role` FOREIGN KEY (`scope_type`, `scope_key`, `role_id`) REFERENCES `un_plat_role` (`scope_type`, `scope_key`, `id`),
  CONSTRAINT `ck_plat_account_role_scope` CHECK (((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0))),
  CONSTRAINT `ck_plat_account_role_validity` CHECK (((`valid_until` is null) or (`valid_until` > `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_account_role`
--

LOCK TABLES `un_plat_account_role` WRITE;
/*!40000 ALTER TABLE `un_plat_account_role` DISABLE KEYS */;
INSERT INTO `un_plat_account_role` VALUES (2085917352828669953,'PLATFORM',0,2085917350597300225,2085917351893340161,'2026-08-08 02:33:45.508',NULL,'2026-08-08 02:33:45.508',2085917350597300225);
/*!40000 ALTER TABLE `un_plat_account_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_authz_epoch`
--

DROP TABLE IF EXISTS `un_plat_authz_epoch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_authz_epoch` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `epoch` bigint NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_authz_epoch_scope` (`scope_type`,`scope_key`),
  KEY `fk_plat_authz_epoch_system` (`system_id`),
  CONSTRAINT `fk_plat_authz_epoch_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_plat_authz_epoch_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`id` > 0)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null) and (`id` = `system_id`)))),
  CONSTRAINT `ck_plat_authz_epoch_value` CHECK ((`epoch` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_authz_epoch`
--

LOCK TABLES `un_plat_authz_epoch` WRITE;
/*!40000 ALTER TABLE `un_plat_authz_epoch` DISABLE KEYS */;
INSERT INTO `un_plat_authz_epoch` VALUES (2085917351851397122,'PLATFORM',0,NULL,1,'2026-08-08 02:33:45.809',2085917350597300225,'2026-08-08 02:33:45.809',2085917350597300225,0),(2085920203721703426,'SYSTEM',2085920203721703426,2085920203721703426,1,'2026-08-08 02:45:06.218',2085917350597300225,'2026-08-08 02:45:06.218',2085917350597300225,0);
/*!40000 ALTER TABLE `un_plat_authz_epoch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_authz_version`
--

DROP TABLE IF EXISTS `un_plat_authz_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_authz_version` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `role_id` bigint NOT NULL,
  `version_no` bigint NOT NULL,
  `snapshot_json` json NOT NULL,
  `checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `published_at` datetime(3) NOT NULL,
  `published_by` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_authz_version_role` (`role_id`,`version_no`),
  UNIQUE KEY `uk_plat_authz_version_scope_id` (`scope_type`,`scope_key`,`id`),
  KEY `idx_plat_authz_version_scope` (`scope_type`,`scope_key`,`published_at`),
  KEY `fk_plat_authz_version_role` (`scope_type`,`scope_key`,`role_id`),
  CONSTRAINT `fk_plat_authz_version_role` FOREIGN KEY (`scope_type`, `scope_key`, `role_id`) REFERENCES `un_plat_role` (`scope_type`, `scope_key`, `id`),
  CONSTRAINT `ck_plat_authz_version_number` CHECK ((`version_no` > 0)),
  CONSTRAINT `ck_plat_authz_version_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null)))),
  CONSTRAINT `ck_plat_authz_version_snapshot` CHECK ((json_type(`snapshot_json`) = _utf8mb4'OBJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_authz_version`
--

LOCK TABLES `un_plat_authz_version` WRITE;
/*!40000 ALTER TABLE `un_plat_authz_version` DISABLE KEYS */;
INSERT INTO `un_plat_authz_version` VALUES (2085917352757366786,'PLATFORM',0,NULL,2085917351893340161,1,'{\"roleId\": \"2085917351893340161\", \"roleCode\": \"platform_root\", \"scopeKey\": \"0\", \"dataScope\": {\"id\": \"2085917351893340162\", \"kind\": \"ALL\"}, \"scopeType\": \"PLATFORM\", \"permissions\": [{\"code\": \"platform.runtime.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917351956254722\"}, {\"code\": \"platform.admin.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917351994003457\"}, {\"code\": \"platform.system.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352027557890\"}, {\"code\": \"platform.organization.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352056918018\"}, {\"code\": \"platform.role.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352090472450\"}, {\"code\": \"platform.permission.explain\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352140804097\"}, {\"code\": \"platform.ai.agent.use\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352182747138\"}, {\"code\": \"platform.ai.policy.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352224690178\"}, {\"code\": \"platform.task.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352258244609\"}, {\"code\": \"platform.task.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352287604738\"}, {\"code\": \"platform.task.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352316964866\"}, {\"code\": \"platform.openapi.application.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352346324993\"}, {\"code\": \"platform.dashboard.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352371490818\"}, {\"code\": \"platform.dashboard.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352405045249\"}, {\"code\": \"platform.flow.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352430211074\"}, {\"code\": \"platform.flow.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352459571201\"}, {\"code\": \"platform.flow.start\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352488931329\"}, {\"code\": \"platform.audit.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352535068673\"}, {\"code\": \"platform.settings.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352585400321\"}]}','a272e25781bc638b03c3d0f9ae0f15b99a84fa2248e3f5d312576c356d9e3637','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508'),(2085920205592363009,'SYSTEM',2085920203721703426,2085920203721703426,2085920203797200900,1,'{\"roleId\": \"2085920203797200900\", \"roleCode\": \"system_owner\", \"scopeKey\": \"2085920203721703426\", \"dataScope\": {\"id\": \"2085920203864309761\", \"kind\": \"ALL\"}, \"scopeType\": \"SYSTEM\", \"permissions\": [{\"code\": \"module.config.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920203931418625\"}, {\"code\": \"flow.external-task.work\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920203994333186\"}, {\"code\": \"work.project.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204036276226\"}, {\"code\": \"work.project.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204057247746\"}, {\"code\": \"work.project.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204082413570\"}, {\"code\": \"work.report.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204124356610\"}, {\"code\": \"work.report.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204162105345\"}, {\"code\": \"work.report.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204195659777\"}, {\"code\": \"system.runtime.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204229214209\"}, {\"code\": \"system.admin.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204258574338\"}, {\"code\": \"system.workbench.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204279545858\"}, {\"code\": \"runtime.saved_view.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204308905985\"}, {\"code\": \"runtime.favorite.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204334071810\"}, {\"code\": \"ai.agent.use\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204355043329\"}, {\"code\": \"ai.policy.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204401180673\"}, {\"code\": \"system.settings.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204426346497\"}, {\"code\": \"system.settings.description.edit\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204468289537\"}, {\"code\": \"system.tenant.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204485066754\"}, {\"code\": \"system.organization.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204514426881\"}, {\"code\": \"system.member.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204547981314\"}, {\"code\": \"system.member.identity.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204568952833\"}, {\"code\": \"system.member.list\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204602507265\"}, {\"code\": \"system.role.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204627673090\"}, {\"code\": \"system.permission.explain\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204678004737\"}, {\"code\": \"system.audit.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204707364865\"}, {\"code\": \"system.access.review\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204740919297\"}, {\"code\": \"openapi.application.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204770279426\"}, {\"code\": \"flow.definition.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204808028161\"}, {\"code\": \"flow.instance.start\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204828999681\"}, {\"code\": \"flow.instance.decide\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204870942721\"}, {\"code\": \"flow.instance.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204896108546\"}, {\"code\": \"flow.instance.withdraw\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204925468673\"}, {\"code\": \"flow.instance.terminate\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204959023106\"}, {\"code\": \"flow.instance.urge\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204979994626\"}, {\"code\": \"flow.instance.comment\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205030326274\"}, {\"code\": \"flow.instance.transfer\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205059686402\"}, {\"code\": \"flow.instance.add-sign\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205093240833\"}, {\"code\": \"flow.instance.return\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205122600962\"}, {\"code\": \"flow.instance.claim\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205164544002\"}, {\"code\": \"flow.instance.cancel-claim\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205189709825\"}, {\"code\": \"flow.instance.reduce-sign\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205223264257\"}, {\"code\": \"flow.instance.copy\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205248430081\"}, {\"code\": \"work.task.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205269401601\"}, {\"code\": \"work.task.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205319733250\"}, {\"code\": \"work.task.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205336510465\"}, {\"code\": \"event.message.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205382647809\"}, {\"code\": \"event.template.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205403619330\"}, {\"code\": \"file.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205462339586\"}, {\"code\": \"file.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205487505409\"}, {\"code\": \"file.reference\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205521059841\"}, {\"code\": \"file.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205542031361\"}]}','6929542166f2c32bb56cf47535d4191ec0c7359e03973f1eb7e67576f233fd0b','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748');
/*!40000 ALTER TABLE `un_plat_authz_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_context_session`
--

DROP TABLE IF EXISTS `un_plat_context_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_context_session` (
  `id` bigint NOT NULL,
  `token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `context_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `permission_version` bigint NOT NULL,
  `authz_epoch` bigint NOT NULL DEFAULT '1',
  `permissions_json` json NOT NULL,
  `role_snapshot_json` json NOT NULL DEFAULT (json_array()),
  `data_scope_snapshot_json` json NOT NULL DEFAULT (json_array()),
  `authz_scope_key` bigint GENERATED ALWAYS AS ((case when (`context_type` = _utf8mb4'PLATFORM') then 0 else `system_id` end)) STORED,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `issued_at` datetime(3) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `last_seen_at` datetime(3) NOT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_context_token` (`token_hash`),
  KEY `idx_plat_context_account` (`account_id`,`status`,`expires_at`),
  KEY `idx_plat_context_system` (`system_id`,`member_id`,`tenant_id`,`status`),
  KEY `fk_plat_context_tenant` (`tenant_id`),
  KEY `fk_plat_context_member` (`member_id`),
  KEY `fk_plat_context_scoped_tenant` (`system_id`,`tenant_id`),
  KEY `fk_plat_context_scoped_member` (`system_id`,`member_id`,`account_id`),
  KEY `fk_plat_context_authz_scope` (`context_type`,`authz_scope_key`),
  CONSTRAINT `fk_plat_context_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_context_authz_scope` FOREIGN KEY (`context_type`, `authz_scope_key`) REFERENCES `un_plat_authz_epoch` (`scope_type`, `scope_key`),
  CONSTRAINT `fk_plat_context_scoped_member` FOREIGN KEY (`system_id`, `member_id`, `account_id`) REFERENCES `un_plat_member` (`system_id`, `id`, `account_id`),
  CONSTRAINT `fk_plat_context_scoped_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_plat_context_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_plat_context_authz_epoch` CHECK (((`authz_epoch` > 0) and (`permission_version` = `authz_epoch`))),
  CONSTRAINT `ck_plat_context_data_scope_snapshot` CHECK ((json_type(`data_scope_snapshot_json`) = _utf8mb4'ARRAY')),
  CONSTRAINT `ck_plat_context_role_snapshot` CHECK ((json_type(`role_snapshot_json`) = _utf8mb4'ARRAY')),
  CONSTRAINT `ck_plat_context_shape` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null) and (`member_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null) and (`member_id` is not null)))),
  CONSTRAINT `ck_plat_context_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REVOKED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_context_session`
--

LOCK TABLES `un_plat_context_session` WRITE;
/*!40000 ALTER TABLE `un_plat_context_session` DISABLE KEYS */;
INSERT INTO `un_plat_context_session` (`id`, `token_hash`, `context_type`, `account_id`, `system_id`, `tenant_id`, `member_id`, `permission_version`, `authz_epoch`, `permissions_json`, `role_snapshot_json`, `data_scope_snapshot_json`, `status`, `issued_at`, `expires_at`, `last_seen_at`, `revoked_at`, `created_at`, `updated_at`, `version`) VALUES (2085919780424155137,'d1b96758e96ea36f79757fc0510535d3c0054ef60aeddc0d7239c143c2943d6b','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\", \"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\", \"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 02:43:24.826','2026-08-08 03:13:24.826','2026-08-08 02:43:24.826','2026-08-08 02:45:28.500','2026-08-08 02:43:24.826','2026-08-08 02:45:28.500',1),(2085920299205033986,'23f4806aa8d1120bc122b044715694ffb76763dc1b9c56c145f66ed324a4f914','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\", \"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\", \"work.task.access\", \"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','ACTIVE','2026-08-08 02:45:28.513','2026-08-08 03:15:28.513','2026-08-08 02:45:28.513',NULL,'2026-08-08 02:45:28.513','2026-08-08 02:45:28.513',0),(2085923663814934529,'bccc0b2c0130a4e7f0da76658a26e25e172802dea0f7cb8e1e4abd5038ba07cb','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.audit.view\", \"platform.settings.manage\", \"platform.task.read\", \"platform.ai.policy.manage\", \"platform.task.manage\", \"platform.dashboard.view\", \"platform.runtime.access\", \"platform.flow.manage\", \"platform.system.manage\", \"platform.task.create\", \"platform.openapi.application.manage\", \"platform.permission.explain\", \"platform.admin.access\", \"platform.flow.start\", \"platform.ai.agent.use\", \"platform.organization.manage\", \"platform.role.manage\", \"platform.flow.read\", \"platform.dashboard.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 02:58:50.698','2026-08-08 03:28:50.698','2026-08-08 02:58:50.698','2026-08-08 02:59:19.608','2026-08-08 02:58:50.698','2026-08-08 02:59:19.608',1),(2085923785227452418,'a237d1ab5d5f5381416cafda564c46b32eaacb85208dddfc79545eaf08aa5ce1','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"flow.instance.comment\", \"system.permission.explain\", \"flow.instance.reduce-sign\", \"system.settings.manage\", \"flow.definition.manage\", \"system.audit.view\", \"ai.agent.use\", \"system.organization.manage\", \"system.settings.description.edit\", \"file.read\", \"flow.instance.add-sign\", \"work.task.access\", \"runtime.favorite.manage\", \"flow.external-task.work\", \"system.admin.access\", \"flow.instance.claim\", \"work.project.manage\", \"work.project.create\", \"system.member.list\", \"flow.instance.read\", \"module.config.manage\", \"system.member.identity.view\", \"flow.instance.copy\", \"system.access.review\", \"system.role.manage\", \"flow.instance.cancel-claim\", \"event.message.access\", \"work.report.manage\", \"work.report.create\", \"system.runtime.access\", \"flow.instance.terminate\", \"system.tenant.manage\", \"flow.instance.transfer\", \"ai.policy.manage\", \"openapi.application.manage\", \"flow.instance.withdraw\", \"event.template.manage\", \"work.project.access\", \"work.task.manage\", \"work.task.create\", \"flow.instance.decide\", \"runtime.saved_view.manage\", \"flow.instance.urge\", \"file.manage\", \"flow.instance.return\", \"file.create\", \"system.workbench.view\", \"flow.instance.start\", \"system.member.manage\", \"work.report.access\", \"file.reference\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','REVOKED','2026-08-08 02:59:19.646','2026-08-08 03:29:19.646','2026-08-08 02:59:19.646','2026-08-08 02:59:43.597','2026-08-08 02:59:19.646','2026-08-08 02:59:43.597',1),(2085923885777502210,'13d1fa4ce242790c3585dd077b773257b18538badc0dcf40b7074fc11c10996c','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.audit.view\", \"platform.settings.manage\", \"platform.task.read\", \"platform.ai.policy.manage\", \"platform.task.manage\", \"platform.dashboard.view\", \"platform.runtime.access\", \"platform.flow.manage\", \"platform.system.manage\", \"platform.task.create\", \"platform.openapi.application.manage\", \"platform.permission.explain\", \"platform.admin.access\", \"platform.flow.start\", \"platform.ai.agent.use\", \"platform.organization.manage\", \"platform.role.manage\", \"platform.flow.read\", \"platform.dashboard.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 02:59:43.618','2026-08-08 03:29:43.618','2026-08-08 02:59:43.618','2026-08-08 02:59:45.679','2026-08-08 02:59:43.618','2026-08-08 02:59:45.679',1),(2085923894447128577,'fcda128f8f0c9a959c621e9fc0aff6cef58fbaab5c40260be255900b6bd55d28','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"flow.instance.comment\", \"system.permission.explain\", \"flow.instance.reduce-sign\", \"system.settings.manage\", \"flow.definition.manage\", \"system.audit.view\", \"ai.agent.use\", \"system.organization.manage\", \"system.settings.description.edit\", \"file.read\", \"flow.instance.add-sign\", \"work.task.access\", \"runtime.favorite.manage\", \"flow.external-task.work\", \"system.admin.access\", \"flow.instance.claim\", \"work.project.manage\", \"work.project.create\", \"system.member.list\", \"flow.instance.read\", \"module.config.manage\", \"system.member.identity.view\", \"flow.instance.copy\", \"system.access.review\", \"system.role.manage\", \"flow.instance.cancel-claim\", \"event.message.access\", \"work.report.manage\", \"work.report.create\", \"system.runtime.access\", \"flow.instance.terminate\", \"system.tenant.manage\", \"flow.instance.transfer\", \"ai.policy.manage\", \"openapi.application.manage\", \"flow.instance.withdraw\", \"event.template.manage\", \"work.project.access\", \"work.task.manage\", \"work.task.create\", \"flow.instance.decide\", \"runtime.saved_view.manage\", \"flow.instance.urge\", \"file.manage\", \"flow.instance.return\", \"file.create\", \"system.workbench.view\", \"flow.instance.start\", \"system.member.manage\", \"work.report.access\", \"file.reference\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','REVOKED','2026-08-08 02:59:45.686','2026-08-08 03:29:45.686','2026-08-08 02:59:45.686','2026-08-08 03:10:49.731','2026-08-08 02:59:45.686','2026-08-08 03:10:49.731',1),(2085926679918301186,'fe34451fb801183644485a5f5081f4ed17dab59b609d4bd55b41d19c0654b30b','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\", \"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\", \"platform.task.read\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:10:49.794','2026-08-08 03:40:49.794','2026-08-08 03:10:49.794','2026-08-08 03:10:52.017','2026-08-08 03:10:49.794','2026-08-08 03:10:52.017',1),(2085926689271599105,'0c7a029fecd81fe1156bb39539242242b2d6c64c0d17c4310dd2c0bfce9284e6','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\", \"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\", \"work.task.access\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','REVOKED','2026-08-08 03:10:52.023','2026-08-08 03:40:52.023','2026-08-08 03:10:52.023','2026-08-08 03:11:52.400','2026-08-08 03:10:52.023','2026-08-08 03:11:52.400',1),(2085926942603366401,'44928ce932d2ead43773de405c3de476f8814752f55ed14c1e76e0468b2f2138','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\", \"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\", \"platform.task.read\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:11:52.422','2026-08-08 03:41:52.422','2026-08-08 03:11:52.422','2026-08-08 03:11:54.562','2026-08-08 03:11:52.422','2026-08-08 03:11:54.562',1),(2085926951595954178,'1e5691aa84e9a3ed6a30713ce834f8542a546013bfb491c6b30b34214f31dcba','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\", \"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\", \"work.task.access\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','ACTIVE','2026-08-08 03:11:54.568','2026-08-08 03:41:54.568','2026-08-08 03:11:54.568',NULL,'2026-08-08 03:11:54.568','2026-08-08 03:11:54.568',0),(2085928402673868802,'a60de9286f27177ea24f3c02d66ed951285ec4604b33174cfba9f86a56445934','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\", \"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','ACTIVE','2026-08-08 03:17:40.531','2026-08-08 03:47:40.531','2026-08-08 03:17:40.531',NULL,'2026-08-08 03:17:40.531','2026-08-08 03:17:40.531',0),(2085928730194485249,'41a7c81237f42e017c8a35cf07db6b7013296ada439e34a0be93df471f987425','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\", \"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:18:58.618','2026-08-08 03:48:58.618','2026-08-08 03:18:58.618','2026-08-08 03:19:22.540','2026-08-08 03:18:58.618','2026-08-08 03:19:22.540',1),(2085928830627094530,'cafb12446dc147b4a12f9931cb725f40929020370ca0ab32fdc35b2130cbb2e7','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\", \"work.task.access\", \"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','REVOKED','2026-08-08 03:19:22.562','2026-08-08 03:49:22.562','2026-08-08 03:19:22.562','2026-08-08 03:19:42.302','2026-08-08 03:19:22.562','2026-08-08 03:19:42.302',1),(2085928913506541569,'83983992529c7a4866967ce6148d5a82363b1f310a6fc3ebfee5897e52af34fe','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\", \"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:19:42.323','2026-08-08 03:49:42.323','2026-08-08 03:19:42.323','2026-08-08 03:19:44.758','2026-08-08 03:19:42.323','2026-08-08 03:19:44.758',1),(2085928923757420545,'1abeb073bbcfebb96cb523b1c99cd791fbcd3c93fa286b784d2b79bae923b497','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\", \"work.task.access\", \"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','REVOKED','2026-08-08 03:19:44.766','2026-08-08 03:49:44.766','2026-08-08 03:19:44.766','2026-08-08 03:20:00.136','2026-08-08 03:19:44.766','2026-08-08 03:20:00.136',1),(2085928988303564801,'27ab49a34b759562de8683448808e144c994fd7f71303ea47e9dfab9d522fbc3','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\", \"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:20:00.156','2026-08-08 03:50:00.156','2026-08-08 03:20:00.156','2026-08-08 03:20:02.007','2026-08-08 03:20:00.156','2026-08-08 03:20:02.007',1),(2085928996100775938,'441814e0f80606b3f1b5913133cb08f5e430749a7b951fb6cc7226d83dd63d3f','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\", \"work.task.access\", \"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','ACTIVE','2026-08-08 03:20:02.015','2026-08-08 03:50:02.015','2026-08-08 03:20:02.015',NULL,'2026-08-08 03:20:02.015','2026-08-08 03:20:02.015',0),(2085930015484817409,'2fd512d0afcee045317a117a15b654c0a2da3fca79fb9ecda306ecf5c64a011b','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\", \"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:24:05.054','2026-08-08 03:54:05.054','2026-08-08 03:24:05.054','2026-08-08 03:24:32.823','2026-08-08 03:24:05.054','2026-08-08 03:24:32.823',1),(2085930132010971138,'553fdc0f27aab19d8eb1c533391b36eddea92be7bbb792920696cb2553413969','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"work.task.access\", \"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\", \"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','REVOKED','2026-08-08 03:24:32.837','2026-08-08 03:54:32.837','2026-08-08 03:24:32.837','2026-08-08 03:27:51.186','2026-08-08 03:24:32.837','2026-08-08 03:27:51.186',1),(2085930271253475330,'295ccb4cbd8cd5fbc90402d1a72bf429b0013f63769e38bc19b4ea1e57061368','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\", \"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:25:06.035','2026-08-08 03:55:06.035','2026-08-08 03:25:06.035','2026-08-08 03:25:40.572','2026-08-08 03:25:06.035','2026-08-08 03:25:40.572',1),(2085930416137318402,'88c2231a695e2faccd7aedfe850ff0bb46dbb90be69259453f38bbd368c5a292','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"work.task.access\", \"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\", \"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','ACTIVE','2026-08-08 03:25:40.578','2026-08-08 03:55:40.578','2026-08-08 03:25:40.578',NULL,'2026-08-08 03:25:40.578','2026-08-08 03:25:40.578',0),(2085930964043444225,'85dfff693cd21530ccbdd25f12b69c526613569a7f3c7dd3646560a007f46259','PLATFORM',2085917350597300225,NULL,NULL,NULL,1,1,'[\"platform.task.read\", \"platform.settings.manage\", \"platform.audit.view\", \"platform.dashboard.manage\", \"platform.flow.read\", \"platform.role.manage\", \"platform.organization.manage\", \"platform.ai.agent.use\", \"platform.flow.start\", \"platform.admin.access\", \"platform.permission.explain\", \"platform.openapi.application.manage\", \"platform.task.create\", \"platform.system.manage\", \"platform.flow.manage\", \"platform.runtime.access\", \"platform.dashboard.view\", \"platform.task.manage\", \"platform.ai.policy.manage\"]','[{\"id\": \"2085917351893340161\", \"code\": \"platform_root\", \"name\": \"平台超级筡芾\碓盶", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085917351893340162\", \"code\": \"platform_root_all\", \"kind\": \"ALL\", \"roleId\": \"2085917351893340161\"}]','REVOKED','2026-08-08 03:27:51.208','2026-08-08 03:57:51.208','2026-08-08 03:27:51.208','2026-08-08 03:28:04.013','2026-08-08 03:27:51.208','2026-08-08 03:28:04.013',1),(2085931017776672769,'2e30092797ac583828058c693c4ab567f143954e3921a5f06c7579f9bd61f7ed','SYSTEM',2085917350597300225,2085920203721703426,2085920203797200898,2085920203797200899,1,1,'[\"work.task.access\", \"flow.instance.add-sign\", \"file.read\", \"system.settings.description.edit\", \"system.organization.manage\", \"ai.agent.use\", \"system.audit.view\", \"flow.definition.manage\", \"system.settings.manage\", \"flow.instance.reduce-sign\", \"system.permission.explain\", \"flow.instance.comment\", \"file.reference\", \"work.report.access\", \"system.member.manage\", \"flow.instance.start\", \"system.workbench.view\", \"file.create\", \"flow.instance.return\", \"file.manage\", \"flow.instance.urge\", \"runtime.saved_view.manage\", \"flow.instance.decide\", \"work.task.create\", \"work.task.manage\", \"work.project.access\", \"event.template.manage\", \"flow.instance.withdraw\", \"openapi.application.manage\", \"ai.policy.manage\", \"flow.instance.transfer\", \"system.tenant.manage\", \"flow.instance.terminate\", \"system.runtime.access\", \"work.report.create\", \"work.report.manage\", \"event.message.access\", \"flow.instance.cancel-claim\", \"system.role.manage\", \"system.access.review\", \"flow.instance.copy\", \"system.member.identity.view\", \"module.config.manage\", \"flow.instance.read\", \"system.member.list\", \"work.project.create\", \"work.project.manage\", \"flow.instance.claim\", \"system.admin.access\", \"flow.external-task.work\", \"runtime.favorite.manage\"]','[{\"id\": \"2085920203797200900\", \"code\": \"system_owner\", \"name\": \"系统\所\覾衆誠運", \"publishedVersion\": \"1\"}]','[{\"id\": \"2085920203864309761\", \"code\": \"system_owner_all\", \"kind\": \"ALL\", \"roleId\": \"2085920203797200900\"}]','ACTIVE','2026-08-08 03:28:04.020','2026-08-08 03:58:04.020','2026-08-08 03:28:04.020',NULL,'2026-08-08 03:28:04.020','2026-08-08 03:28:04.020',0);
/*!40000 ALTER TABLE `un_plat_context_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_credential`
--

DROP TABLE IF EXISTS `un_plat_credential`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_credential` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `credential_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_algorithm` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_parameters` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `failed_attempts` int NOT NULL DEFAULT '0',
  `locked_until` datetime(3) DEFAULT NULL,
  `password_changed_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_credential_account_type` (`account_id`,`credential_type`),
  CONSTRAINT `fk_plat_credential_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `ck_plat_credential_failed` CHECK ((`failed_attempts` >= 0)),
  CONSTRAINT `ck_plat_credential_type` CHECK ((`credential_type` = _utf8mb4'PASSWORD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_credential`
--

LOCK TABLES `un_plat_credential` WRITE;
/*!40000 ALTER TABLE `un_plat_credential` DISABLE KEYS */;
INSERT INTO `un_plat_credential` VALUES (2085917351759122434,2085917350597300225,'PASSWORD','$argon2id$v=19$m=65536,t=3,p=1$2TzlUFqSVuRJB1AQnQ4NAg$xEsAxNRLTYnZ7RTicr5GuaTi5YOKoELG4B3lNwz6nMY','ARGON2ID','m=65536,t=3,p=1',0,NULL,'2026-08-08 02:33:45.508','2026-08-08 02:33:45.508','2026-08-08 03:25:05.722',6);
/*!40000 ALTER TABLE `un_plat_credential` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_data_scope`
--

DROP TABLE IF EXISTS `un_plat_data_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_data_scope` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `scope_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_rule_json` json DEFAULT NULL,
  `is_builtin` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_data_scope_code` (`scope_type`,`scope_key`,`scope_code`),
  UNIQUE KEY `uk_plat_data_scope_scope_id` (`scope_type`,`scope_key`,`id`),
  UNIQUE KEY `uk_plat_data_scope_scope_tenant_id` (`scope_type`,`scope_key`,`tenant_key`,`id`),
  KEY `idx_plat_data_scope_system` (`system_id`,`tenant_id`,`status`,`scope_kind`),
  CONSTRAINT `fk_plat_data_scope_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_data_scope_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_data_scope_builtin` CHECK ((`is_builtin` in (0,1))),
  CONSTRAINT `ck_plat_data_scope_field_rule` CHECK ((((`scope_kind` = _utf8mb4'FIELD_RULE') and (`field_rule_json` is not null)) or ((`scope_kind` <> _utf8mb4'FIELD_RULE') and (`field_rule_json` is null)))),
  CONSTRAINT `ck_plat_data_scope_kind` CHECK ((`scope_kind` in (_utf8mb4'ALL',_utf8mb4'SELF',_utf8mb4'PRIMARY_DEPARTMENT',_utf8mb4'DEPARTMENT_TREE',_utf8mb4'SELECTED_DEPARTMENTS',_utf8mb4'SELECTED_MEMBERS',_utf8mb4'FIELD_RULE'))),
  CONSTRAINT `ck_plat_data_scope_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null)))),
  CONSTRAINT `ck_plat_data_scope_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_data_scope`
--

LOCK TABLES `un_plat_data_scope` WRITE;
/*!40000 ALTER TABLE `un_plat_data_scope` DISABLE KEYS */;
INSERT INTO `un_plat_data_scope` (`id`, `scope_type`, `scope_key`, `system_id`, `tenant_id`, `scope_code`, `name`, `scope_kind`, `field_rule_json`, `is_builtin`, `status`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `deleted_by`, `version`) VALUES (2085917351893340162,'PLATFORM',0,NULL,NULL,'platform_root_all','平台','ALL',NULL,1,'ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,NULL,NULL,0),(2085920203864309761,'SYSTEM',2085920203721703426,2085920203721703426,NULL,'system_owner_all','系统','ALL',NULL,1,'ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,NULL,NULL,0);
/*!40000 ALTER TABLE `un_plat_data_scope` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_data_scope_target`
--

DROP TABLE IF EXISTS `un_plat_data_scope_target`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_data_scope_target` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `data_scope_id` bigint NOT NULL,
  `target_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `department_id` bigint DEFAULT NULL,
  `account_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `target_id` bigint GENERATED ALWAYS AS (coalesce(`department_id`,`account_id`,`member_id`)) STORED,
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`deleted_at` is null) then 1 else NULL end)) STORED,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_data_scope_target` (`scope_type`,`scope_key`,`data_scope_id`,`target_type`,`target_id`,`active_marker`),
  KEY `idx_plat_data_scope_target_lookup` (`scope_type`,`scope_key`,`target_type`,`target_id`),
  KEY `fk_plat_data_scope_target_scope` (`scope_type`,`scope_key`,`tenant_key`,`data_scope_id`),
  KEY `fk_plat_data_scope_target_department` (`scope_type`,`scope_key`,`tenant_key`,`department_id`),
  KEY `fk_plat_data_scope_target_account` (`account_id`),
  KEY `fk_plat_data_scope_target_tenant_access` (`system_id`,`member_id`,`tenant_id`),
  CONSTRAINT `fk_plat_data_scope_target_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_data_scope_target_department` FOREIGN KEY (`scope_type`, `scope_key`, `tenant_key`, `department_id`) REFERENCES `un_plat_department` (`scope_type`, `scope_key`, `tenant_key`, `id`),
  CONSTRAINT `fk_plat_data_scope_target_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`),
  CONSTRAINT `fk_plat_data_scope_target_scope` FOREIGN KEY (`scope_type`, `scope_key`, `tenant_key`, `data_scope_id`) REFERENCES `un_plat_data_scope` (`scope_type`, `scope_key`, `tenant_key`, `id`),
  CONSTRAINT `fk_plat_data_scope_target_tenant_access` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`),
  CONSTRAINT `ck_plat_data_scope_target_shape` CHECK ((((`target_type` = _utf8mb4'DEPARTMENT') and (`department_id` is not null) and (`account_id` is null) and (`member_id` is null) and (((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null)))) or ((`target_type` = _utf8mb4'ACCOUNT') and (`department_id` is null) and (`account_id` is not null) and (`member_id` is null) and (`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`tenant_id` is null)) or ((`target_type` = _utf8mb4'MEMBER') and (`department_id` is null) and (`account_id` is null) and (`member_id` is not null) and (`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_data_scope_target`
--

LOCK TABLES `un_plat_data_scope_target` WRITE;
/*!40000 ALTER TABLE `un_plat_data_scope_target` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_data_scope_target` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_department`
--

DROP TABLE IF EXISTS `un_plat_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_department` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `parent_id` bigint DEFAULT NULL,
  `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_department_code` (`scope_type`,`scope_key`,`department_code`),
  UNIQUE KEY `uk_plat_department_scope_id` (`scope_type`,`scope_key`,`id`),
  UNIQUE KEY `uk_plat_department_scope_tenant_id` (`scope_type`,`scope_key`,`tenant_key`,`id`),
  UNIQUE KEY `uk_plat_department_system_tenant_id` (`system_id`,`tenant_id`,`id`),
  KEY `idx_plat_department_tree` (`scope_type`,`scope_key`,`parent_id`,`status`,`sort_order`),
  KEY `idx_plat_department_system` (`system_id`,`tenant_id`,`status`),
  KEY `fk_plat_department_parent` (`scope_type`,`scope_key`,`tenant_key`,`parent_id`),
  CONSTRAINT `fk_plat_department_parent` FOREIGN KEY (`scope_type`, `scope_key`, `tenant_key`, `parent_id`) REFERENCES `un_plat_department` (`scope_type`, `scope_key`, `tenant_key`, `id`),
  CONSTRAINT `fk_plat_department_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_department_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_department_parent` CHECK (((`parent_id` is null) or (`parent_id` <> `id`))),
  CONSTRAINT `ck_plat_department_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null)))),
  CONSTRAINT `ck_plat_department_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_department`
--

LOCK TABLES `un_plat_department` WRITE;
/*!40000 ALTER TABLE `un_plat_department` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_department_closure`
--

DROP TABLE IF EXISTS `un_plat_department_closure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_department_closure` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `ancestor_id` bigint NOT NULL,
  `descendant_id` bigint NOT NULL,
  `node_low` bigint GENERATED ALWAYS AS (least(`ancestor_id`,`descendant_id`)) STORED,
  `node_high` bigint GENERATED ALWAYS AS (greatest(`ancestor_id`,`descendant_id`)) STORED,
  `depth` int NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_department_closure_path` (`scope_type`,`scope_key`,`tenant_key`,`ancestor_id`,`descendant_id`),
  UNIQUE KEY `uk_plat_department_closure_acyclic` (`scope_type`,`scope_key`,`tenant_key`,`node_low`,`node_high`),
  KEY `idx_plat_department_closure_desc` (`scope_type`,`scope_key`,`tenant_key`,`descendant_id`,`depth`),
  CONSTRAINT `fk_plat_department_closure_ancestor` FOREIGN KEY (`scope_type`, `scope_key`, `tenant_key`, `ancestor_id`) REFERENCES `un_plat_department` (`scope_type`, `scope_key`, `tenant_key`, `id`),
  CONSTRAINT `fk_plat_department_closure_descendant` FOREIGN KEY (`scope_type`, `scope_key`, `tenant_key`, `descendant_id`) REFERENCES `un_plat_department` (`scope_type`, `scope_key`, `tenant_key`, `id`),
  CONSTRAINT `ck_plat_department_closure_depth` CHECK ((((`ancestor_id` = `descendant_id`) and (`depth` = 0)) or ((`ancestor_id` <> `descendant_id`) and (`depth` > 0)))),
  CONSTRAINT `ck_plat_department_closure_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_department_closure`
--

LOCK TABLES `un_plat_department_closure` WRITE;
/*!40000 ALTER TABLE `un_plat_department_closure` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_department_closure` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_department_leader_assignment`
--

DROP TABLE IF EXISTS `un_plat_department_leader_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_department_leader_assignment` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `assignment_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `leader_member_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `assigned_by` bigint NOT NULL,
  `assigned_at` datetime(3) NOT NULL,
  `cleared_by` bigint DEFAULT NULL,
  `cleared_at` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `active_department_id` bigint GENERATED ALWAYS AS ((case when (`status` = _ascii'ACTIVE') then `department_id` else NULL end)) STORED,
  PRIMARY KEY (`system_id`,`tenant_id`,`assignment_id`),
  UNIQUE KEY `uk_plat_department_leader_active` (`system_id`,`tenant_id`,`active_department_id`),
  KEY `idx_plat_department_leader_member` (`system_id`,`tenant_id`,`leader_member_id`,`status`,`department_id`),
  KEY `idx_plat_department_leader_history` (`system_id`,`tenant_id`,`department_id`,`assigned_at`,`assignment_id`),
  KEY `fk_plat_department_leader_member` (`system_id`,`leader_member_id`,`tenant_id`),
  KEY `fk_plat_department_leader_assigner` (`assigned_by`),
  KEY `fk_plat_department_leader_clearer` (`cleared_by`),
  CONSTRAINT `fk_plat_department_leader_assigner` FOREIGN KEY (`assigned_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_plat_department_leader_clearer` FOREIGN KEY (`cleared_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_plat_department_leader_department` FOREIGN KEY (`system_id`, `tenant_id`, `department_id`) REFERENCES `un_plat_department` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_plat_department_leader_member` FOREIGN KEY (`system_id`, `leader_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_plat_department_leader_audit` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`cleared_by` is null) and (`cleared_at` is null)) or ((`status` = _utf8mb4'CLEARED') and (`cleared_by` is not null) and (`cleared_at` is not null) and (`cleared_at` >= `assigned_at`)))),
  CONSTRAINT `ck_plat_department_leader_identity` CHECK (((`assignment_id` > 0) and (`department_id` > 0) and (`leader_member_id` > 0) and (`assigned_by` > 0))),
  CONSTRAINT `ck_plat_department_leader_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CLEARED'))),
  CONSTRAINT `ck_plat_department_leader_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_department_leader_assignment`
--

LOCK TABLES `un_plat_department_leader_assignment` WRITE;
/*!40000 ALTER TABLE `un_plat_department_leader_assignment` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_department_leader_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_global_setting`
--

DROP TABLE IF EXISTS `un_plat_global_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_global_setting` (
  `id` tinyint NOT NULL,
  `profile_json` json NOT NULL,
  `storage_policy_json` json NOT NULL,
  `security_policy_json` json NOT NULL,
  `quota_policy_json` json NOT NULL,
  `backup_policy_json` json NOT NULL,
  `release_policy_json` json NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_plat_global_setting_actor` (`updated_by`),
  CONSTRAINT `fk_plat_global_setting_actor` FOREIGN KEY (`updated_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `ck_plat_global_setting_json` CHECK (((json_type(`profile_json`) = _utf8mb4'OBJECT') and (json_type(`storage_policy_json`) = _utf8mb4'OBJECT') and (json_type(`security_policy_json`) = _utf8mb4'OBJECT') and (json_type(`quota_policy_json`) = _utf8mb4'OBJECT') and (json_type(`backup_policy_json`) = _utf8mb4'OBJECT') and (json_type(`release_policy_json`) = _utf8mb4'OBJECT'))),
  CONSTRAINT `ck_plat_global_setting_singleton` CHECK ((`id` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_global_setting`
--

LOCK TABLES `un_plat_global_setting` WRITE;
/*!40000 ALTER TABLE `un_plat_global_setting` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_global_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_identity_auth_state`
--

DROP TABLE IF EXISTS `un_plat_identity_auth_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_identity_auth_state` (
  `id` bigint NOT NULL,
  `state_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_id` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `redirect_uri` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `nonce` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `code_verifier` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `expires_at` datetime(3) NOT NULL,
  `consumed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_identity_auth_state_hash` (`state_hash`),
  KEY `idx_plat_identity_auth_state_expiry` (`status`,`expires_at`),
  KEY `fk_plat_identity_auth_state_provider` (`provider_id`),
  KEY `fk_plat_identity_auth_state_scope` (`system_id`,`tenant_id`),
  CONSTRAINT `fk_plat_identity_auth_state_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_plat_identity_provider` (`id`),
  CONSTRAINT `fk_plat_identity_auth_state_scope` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_identity_auth_state_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CONSUMED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_identity_auth_state`
--

LOCK TABLES `un_plat_identity_auth_state` WRITE;
/*!40000 ALTER TABLE `un_plat_identity_auth_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_identity_auth_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_identity_binding`
--

DROP TABLE IF EXISTS `un_plat_identity_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_identity_binding` (
  `id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `external_user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `email_normalized` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_department_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attributes_json` json NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `scope_system_key` bigint GENERATED ALWAYS AS (ifnull(`system_id`,0)) STORED,
  `scope_tenant_key` bigint GENERATED ALWAYS AS (ifnull(`tenant_id`,0)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_identity_binding_external_scope` (`provider_id`,`external_user_id`,`scope_system_key`,`scope_tenant_key`),
  UNIQUE KEY `uk_plat_identity_binding_account_scope` (`provider_id`,`account_id`,`scope_system_key`,`scope_tenant_key`),
  KEY `idx_plat_identity_binding_scope` (`system_id`,`tenant_id`,`account_id`),
  KEY `fk_plat_identity_binding_account` (`account_id`),
  KEY `fk_plat_identity_binding_tenant` (`tenant_id`),
  KEY `fk_plat_identity_binding_member` (`member_id`),
  KEY `fk_plat_identity_binding_member_tenant` (`system_id`,`member_id`,`tenant_id`),
  CONSTRAINT `fk_plat_identity_binding_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_identity_binding_member` FOREIGN KEY (`member_id`) REFERENCES `un_plat_member` (`id`),
  CONSTRAINT `fk_plat_identity_binding_member_scope` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`),
  CONSTRAINT `fk_plat_identity_binding_member_tenant` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`),
  CONSTRAINT `fk_plat_identity_binding_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_plat_identity_provider` (`id`),
  CONSTRAINT `fk_plat_identity_binding_scope` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_plat_identity_binding_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_identity_binding_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `un_plat_tenant` (`id`),
  CONSTRAINT `ck_plat_identity_binding_scope` CHECK ((((`system_id` is null) and (`tenant_id` is null) and (`member_id` is null)) or ((`system_id` is not null) and (`tenant_id` is not null)))),
  CONSTRAINT `ck_plat_identity_binding_status` CHECK ((`status` in (_ascii'ACTIVE',_ascii'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_identity_binding`
--

LOCK TABLES `un_plat_identity_binding` WRITE;
/*!40000 ALTER TABLE `un_plat_identity_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_identity_binding` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_identity_provider`
--

DROP TABLE IF EXISTS `un_plat_identity_provider`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_identity_provider` (
  `id` bigint NOT NULL,
  `provider_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `protocol` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issuer_uri` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `authorization_endpoint` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `token_endpoint` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `jwks_uri` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `directory_endpoint` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `client_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `secret_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `callback_uri` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scopes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'openid profile email',
  `allowed_domains_json` json NOT NULL,
  `attribute_mapping_json` json NOT NULL,
  `jit_account` tinyint(1) NOT NULL DEFAULT '0',
  `jit_system_member` tinyint(1) NOT NULL DEFAULT '0',
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `mfa_policy` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPTIONAL',
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
  `preflight_status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_RUN',
  `preflight_version` bigint DEFAULT NULL,
  `preflight_failure_code` varchar(96) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `preflight_at` datetime(3) DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `disabled_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_identity_provider_code` (`provider_code`),
  KEY `idx_plat_identity_provider_scope` (`system_id`,`tenant_id`,`status`),
  KEY `fk_plat_identity_provider_tenant` (`tenant_id`),
  CONSTRAINT `fk_plat_identity_provider_scope` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_plat_identity_provider_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_identity_provider_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `un_plat_tenant` (`id`),
  CONSTRAINT `ck_plat_identity_provider_jit` CHECK (((`jit_system_member` = false) or ((`jit_account` = true) and (`system_id` is not null) and (`tenant_id` is not null)))),
  CONSTRAINT `ck_plat_identity_provider_mfa` CHECK ((`mfa_policy` in (_utf8mb4'DISABLED',_utf8mb4'OPTIONAL',_utf8mb4'REQUIRED'))),
  CONSTRAINT `ck_plat_identity_provider_preflight` CHECK ((`preflight_status` in (_utf8mb4'NOT_RUN',_utf8mb4'PASSED',_utf8mb4'FAILED'))),
  CONSTRAINT `ck_plat_identity_provider_protocol` CHECK ((`protocol` in (_utf8mb4'OIDC',_utf8mb4'OAUTH2',_utf8mb4'SAML2',_utf8mb4'LDAP',_utf8mb4'AD',_utf8mb4'WECOM',_utf8mb4'DINGTALK'))),
  CONSTRAINT `ck_plat_identity_provider_scope` CHECK ((((`system_id` is null) and (`tenant_id` is null)) or ((`system_id` is not null) and (`tenant_id` is not null)))),
  CONSTRAINT `ck_plat_identity_provider_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_identity_provider`
--

LOCK TABLES `un_plat_identity_provider` WRITE;
/*!40000 ALTER TABLE `un_plat_identity_provider` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_identity_provider` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_identity_sync_failure`
--

DROP TABLE IF EXISTS `un_plat_identity_sync_failure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_identity_sync_failure` (
  `id` bigint NOT NULL,
  `job_id` bigint NOT NULL,
  `snapshot_item_id` bigint NOT NULL,
  `failure_code` varchar(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `failure_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_identity_sync_failure_item` (`job_id`,`snapshot_item_id`),
  KEY `idx_plat_identity_sync_failure_job` (`job_id`,`created_at`),
  KEY `fk_plat_identity_sync_failure_item` (`snapshot_item_id`),
  CONSTRAINT `fk_plat_identity_sync_failure_item` FOREIGN KEY (`snapshot_item_id`) REFERENCES `un_plat_identity_sync_item` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_plat_identity_sync_failure_job` FOREIGN KEY (`job_id`) REFERENCES `un_sys_job` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_identity_sync_failure`
--

LOCK TABLES `un_plat_identity_sync_failure` WRITE;
/*!40000 ALTER TABLE `un_plat_identity_sync_failure` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_identity_sync_failure` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_identity_sync_item`
--

DROP TABLE IF EXISTS `un_plat_identity_sync_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_identity_sync_item` (
  `id` bigint NOT NULL,
  `snapshot_id` bigint NOT NULL,
  `item_kind` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `external_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_external_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email_normalized` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department_external_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_department_id` bigint DEFAULT NULL,
  `target_account_id` bigint DEFAULT NULL,
  `target_member_id` bigint DEFAULT NULL,
  `proposed_action` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issue_code` varchar(96) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `attributes_json` json NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_identity_sync_item_external` (`snapshot_id`,`item_kind`,`external_id`),
  KEY `idx_plat_identity_sync_item_issue` (`snapshot_id`,`issue_code`,`item_kind`),
  KEY `fk_plat_identity_sync_item_department` (`target_department_id`),
  KEY `fk_plat_identity_sync_item_account` (`target_account_id`),
  KEY `fk_plat_identity_sync_item_member` (`target_member_id`),
  CONSTRAINT `fk_plat_identity_sync_item_account` FOREIGN KEY (`target_account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_identity_sync_item_department` FOREIGN KEY (`target_department_id`) REFERENCES `un_plat_department` (`id`),
  CONSTRAINT `fk_plat_identity_sync_item_member` FOREIGN KEY (`target_member_id`) REFERENCES `un_plat_member` (`id`),
  CONSTRAINT `fk_plat_identity_sync_item_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `un_plat_identity_sync_snapshot` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_plat_identity_sync_item_action` CHECK ((`proposed_action` in (_utf8mb4'BIND',_utf8mb4'CREATE',_utf8mb4'UPDATE',_utf8mb4'SKIP',_utf8mb4'UNMATCHED'))),
  CONSTRAINT `ck_plat_identity_sync_item_kind` CHECK ((`item_kind` in (_utf8mb4'DEPARTMENT',_utf8mb4'EMPLOYEE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_identity_sync_item`
--

LOCK TABLES `un_plat_identity_sync_item` WRITE;
/*!40000 ALTER TABLE `un_plat_identity_sync_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_identity_sync_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_identity_sync_snapshot`
--

DROP TABLE IF EXISTS `un_plat_identity_sync_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_identity_sync_snapshot` (
  `id` bigint NOT NULL,
  `policy_id` bigint NOT NULL,
  `source_version` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
  `department_count` int NOT NULL DEFAULT '0',
  `employee_count` int NOT NULL DEFAULT '0',
  `matched_count` int NOT NULL DEFAULT '0',
  `create_count` int NOT NULL DEFAULT '0',
  `unmatched_count` int NOT NULL DEFAULT '0',
  `confirmed_at` datetime(3) DEFAULT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `applied_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_identity_sync_snapshot_source` (`policy_id`,`source_version`),
  KEY `idx_plat_identity_sync_snapshot_status` (`policy_id`,`status`,`created_at`),
  KEY `fk_plat_identity_sync_snapshot_confirmer` (`confirmed_by`),
  CONSTRAINT `fk_plat_identity_sync_snapshot_confirmer` FOREIGN KEY (`confirmed_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_identity_sync_snapshot_policy` FOREIGN KEY (`policy_id`) REFERENCES `un_plat_system_identity_policy` (`id`),
  CONSTRAINT `ck_plat_identity_sync_snapshot_counts` CHECK (((`department_count` >= 0) and (`employee_count` >= 0) and (`matched_count` >= 0) and (`create_count` >= 0) and (`unmatched_count` >= 0))),
  CONSTRAINT `ck_plat_identity_sync_snapshot_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'CONFIRMED',_utf8mb4'QUEUED',_utf8mb4'APPLIED',_utf8mb4'PARTIAL_FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_identity_sync_snapshot`
--

LOCK TABLES `un_plat_identity_sync_snapshot` WRITE;
/*!40000 ALTER TABLE `un_plat_identity_sync_snapshot` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_identity_sync_snapshot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_member`
--

DROP TABLE IF EXISTS `un_plat_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_member` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `member_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_tenant_id` bigint NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `joined_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_account` (`system_id`,`account_id`),
  UNIQUE KEY `uk_plat_member_code` (`system_id`,`member_code`),
  UNIQUE KEY `uk_plat_member_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_plat_member_system_id_account` (`system_id`,`id`,`account_id`),
  KEY `idx_plat_member_tenant` (`system_id`,`default_tenant_id`,`status`),
  KEY `fk_plat_member_account` (`account_id`),
  KEY `fk_plat_member_tenant` (`default_tenant_id`),
  CONSTRAINT `fk_plat_member_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_member_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_member_system_tenant` FOREIGN KEY (`system_id`, `default_tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_member_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'PENDING')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_member`
--

LOCK TABLES `un_plat_member` WRITE;
/*!40000 ALTER TABLE `un_plat_member` DISABLE KEYS */;
INSERT INTO `un_plat_member` VALUES (2085920203797200899,2085920203721703426,2085917350597300225,'OWNER_2085920203797200899','Examine2 Root',2085920203797200898,'ACTIVE','2026-08-08 02:45:05.748','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,NULL,0);
/*!40000 ALTER TABLE `un_plat_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_member_department`
--

DROP TABLE IF EXISTS `un_plat_member_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_member_department` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `account_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `principal_id` bigint GENERATED ALWAYS AS (coalesce(`account_id`,`member_id`)) STORED,
  `department_id` bigint NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`deleted_at` is null) then 1 else NULL end)) STORED,
  `primary_principal_id` bigint GENERATED ALWAYS AS ((case when ((`is_primary` = 1) and (`deleted_at` is null)) then coalesce(`account_id`,`member_id`) else NULL end)) STORED,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_department_active` (`scope_type`,`scope_key`,`principal_id`,`department_id`,`active_marker`),
  UNIQUE KEY `uk_plat_member_department_primary` (`scope_type`,`scope_key`,`primary_principal_id`),
  KEY `idx_plat_member_department_dept` (`scope_type`,`scope_key`,`department_id`,`is_primary`),
  KEY `fk_plat_member_department_account` (`account_id`),
  KEY `fk_plat_member_department_department` (`scope_type`,`scope_key`,`tenant_key`,`department_id`),
  KEY `fk_plat_member_department_tenant_access` (`system_id`,`member_id`,`tenant_id`),
  CONSTRAINT `fk_plat_member_department_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_member_department_department` FOREIGN KEY (`scope_type`, `scope_key`, `tenant_key`, `department_id`) REFERENCES `un_plat_department` (`scope_type`, `scope_key`, `tenant_key`, `id`),
  CONSTRAINT `fk_plat_member_department_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`),
  CONSTRAINT `fk_plat_member_department_tenant_access` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`),
  CONSTRAINT `ck_plat_member_department_primary` CHECK ((`is_primary` in (0,1))),
  CONSTRAINT `ck_plat_member_department_principal` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`tenant_id` is null) and (`account_id` is not null) and (`member_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null) and (`account_id` is null) and (`member_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_member_department`
--

LOCK TABLES `un_plat_member_department` WRITE;
/*!40000 ALTER TABLE `un_plat_member_department` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_member_department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_member_manager_assignment`
--

DROP TABLE IF EXISTS `un_plat_member_manager_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_member_manager_assignment` (
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `assignment_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `manager_member_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `assigned_by` bigint NOT NULL,
  `assigned_at` datetime(3) NOT NULL,
  `cleared_by` bigint DEFAULT NULL,
  `cleared_at` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `active_member_id` bigint GENERATED ALWAYS AS ((case when (`status` = _ascii'ACTIVE') then `member_id` else NULL end)) STORED,
  PRIMARY KEY (`system_id`,`tenant_id`,`assignment_id`),
  UNIQUE KEY `uk_plat_member_manager_active` (`system_id`,`tenant_id`,`active_member_id`),
  KEY `idx_plat_member_manager_target` (`system_id`,`tenant_id`,`manager_member_id`,`status`,`member_id`),
  KEY `idx_plat_member_manager_history` (`system_id`,`tenant_id`,`member_id`,`assigned_at`,`assignment_id`),
  KEY `fk_plat_member_manager_member` (`system_id`,`member_id`,`tenant_id`),
  KEY `fk_plat_member_manager_manager` (`system_id`,`manager_member_id`,`tenant_id`),
  KEY `fk_plat_member_manager_assigner` (`assigned_by`),
  KEY `fk_plat_member_manager_clearer` (`cleared_by`),
  CONSTRAINT `fk_plat_member_manager_assigner` FOREIGN KEY (`assigned_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_plat_member_manager_clearer` FOREIGN KEY (`cleared_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_plat_member_manager_manager` FOREIGN KEY (`system_id`, `manager_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_plat_member_manager_member` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_plat_member_manager_audit` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`cleared_by` is null) and (`cleared_at` is null)) or ((`status` = _utf8mb4'CLEARED') and (`cleared_by` is not null) and (`cleared_at` is not null) and (`cleared_at` >= `assigned_at`)))),
  CONSTRAINT `ck_plat_member_manager_identity` CHECK (((`assignment_id` > 0) and (`member_id` > 0) and (`manager_member_id` > 0) and (`member_id` <> `manager_member_id`) and (`assigned_by` > 0))),
  CONSTRAINT `ck_plat_member_manager_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CLEARED'))),
  CONSTRAINT `ck_plat_member_manager_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_member_manager_assignment`
--

LOCK TABLES `un_plat_member_manager_assignment` WRITE;
/*!40000 ALTER TABLE `un_plat_member_manager_assignment` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_member_manager_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_member_role`
--

DROP TABLE IF EXISTS `un_plat_member_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_member_role` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `valid_from` datetime(3) NOT NULL,
  `valid_until` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_role_scope` (`system_id`,`member_id`,`role_id`,`tenant_key`),
  KEY `idx_plat_member_role_valid` (`member_id`,`valid_from`,`valid_until`),
  KEY `fk_plat_member_role_role` (`role_id`),
  KEY `fk_plat_member_role_tenant` (`tenant_id`),
  KEY `fk_plat_member_role_scoped_role` (`system_id`,`role_id`),
  KEY `fk_plat_member_role_scoped_tenant` (`system_id`,`tenant_id`),
  CONSTRAINT `fk_plat_member_role_scoped_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`),
  CONSTRAINT `fk_plat_member_role_scoped_role` FOREIGN KEY (`system_id`, `role_id`) REFERENCES `un_plat_role` (`system_id`, `id`),
  CONSTRAINT `fk_plat_member_role_scoped_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_member_role_tenant` CHECK (((`tenant_id` is null) or (`tenant_id` > 0))),
  CONSTRAINT `ck_plat_member_role_validity` CHECK (((`valid_until` is null) or (`valid_until` > `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_member_role`
--

LOCK TABLES `un_plat_member_role` WRITE;
/*!40000 ALTER TABLE `un_plat_member_role` DISABLE KEYS */;
INSERT INTO `un_plat_member_role` (`id`, `system_id`, `member_id`, `role_id`, `tenant_id`, `valid_from`, `valid_until`, `created_at`, `created_by`) VALUES (2085920205651083266,2085920203721703426,2085920203797200899,2085920203797200900,NULL,'2026-08-08 02:45:05.748',NULL,'2026-08-08 02:45:05.748',2085917350597300225);
/*!40000 ALTER TABLE `un_plat_member_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_member_tenant`
--

DROP TABLE IF EXISTS `un_plat_member_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_member_tenant` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `granted_at` datetime(3) NOT NULL,
  `granted_by` bigint NOT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_tenant` (`system_id`,`member_id`,`tenant_id`),
  UNIQUE KEY `uk_plat_member_tenant_owner` (`system_id`,`member_id`,`tenant_id`,`id`),
  KEY `idx_plat_member_tenant_access` (`system_id`,`tenant_id`,`status`,`member_id`),
  CONSTRAINT `fk_plat_member_tenant_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `un_plat_member` (`system_id`, `id`),
  CONSTRAINT `fk_plat_member_tenant_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_member_tenant_expiry` CHECK (((`expires_at` is null) or (`expires_at` > `granted_at`))),
  CONSTRAINT `ck_plat_member_tenant_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_member_tenant`
--

LOCK TABLES `un_plat_member_tenant` WRITE;
/*!40000 ALTER TABLE `un_plat_member_tenant` DISABLE KEYS */;
INSERT INTO `un_plat_member_tenant` VALUES (2085920205625917442,2085920203721703426,2085920203797200899,2085920203797200898,'ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,NULL,'2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,NULL,NULL,0);
/*!40000 ALTER TABLE `un_plat_member_tenant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_mfa_challenge`
--

DROP TABLE IF EXISTS `un_plat_mfa_challenge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_mfa_challenge` (
  `id` bigint NOT NULL,
  `challenge_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `auth_method` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `expires_at` datetime(3) NOT NULL,
  `consumed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_mfa_challenge_hash` (`challenge_hash`),
  KEY `idx_plat_mfa_challenge_expiry` (`status`,`expires_at`),
  KEY `fk_plat_mfa_challenge_provider` (`provider_id`),
  KEY `fk_plat_mfa_challenge_account` (`account_id`),
  KEY `fk_plat_mfa_challenge_scope` (`system_id`,`tenant_id`),
  CONSTRAINT `fk_plat_mfa_challenge_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_mfa_challenge_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_plat_identity_provider` (`id`),
  CONSTRAINT `fk_plat_mfa_challenge_scope` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_mfa_challenge_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CONSUMED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_mfa_challenge`
--

LOCK TABLES `un_plat_mfa_challenge` WRITE;
/*!40000 ALTER TABLE `un_plat_mfa_challenge` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_mfa_challenge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_mfa_enrollment`
--

DROP TABLE IF EXISTS `un_plat_mfa_enrollment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_mfa_enrollment` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `scope_system_id` bigint GENERATED ALWAYS AS (ifnull(`system_id`,0)) STORED,
  `scope_tenant_id` bigint GENERATED ALWAYS AS (ifnull(`tenant_id`,0)) STORED,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `secret_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `verified_at` datetime(3) NOT NULL,
  `last_used_step` bigint DEFAULT NULL,
  `recovery_regenerated_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_mfa_enrollment_scope` (`account_id`,`scope_system_id`,`scope_tenant_id`),
  KEY `fk_plat_mfa_enrollment_tenant` (`tenant_id`),
  KEY `fk_plat_mfa_enrollment_scope` (`system_id`,`tenant_id`),
  CONSTRAINT `fk_plat_mfa_enrollment_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_mfa_enrollment_scope` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_plat_mfa_enrollment_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_mfa_enrollment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `un_plat_tenant` (`id`),
  CONSTRAINT `ck_plat_mfa_enrollment_scope` CHECK ((((`system_id` is null) and (`tenant_id` is null)) or ((`system_id` is not null) and (`tenant_id` is not null)))),
  CONSTRAINT `ck_plat_mfa_enrollment_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_mfa_enrollment`
--

LOCK TABLES `un_plat_mfa_enrollment` WRITE;
/*!40000 ALTER TABLE `un_plat_mfa_enrollment` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_mfa_enrollment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_mfa_recovery_code`
--

DROP TABLE IF EXISTS `un_plat_mfa_recovery_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_mfa_recovery_code` (
  `id` bigint NOT NULL,
  `enrollment_id` bigint NOT NULL,
  `code_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `used_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_mfa_recovery_hash` (`enrollment_id`,`code_hash`),
  CONSTRAINT `fk_plat_mfa_recovery_enrollment` FOREIGN KEY (`enrollment_id`) REFERENCES `un_plat_mfa_enrollment` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_plat_mfa_recovery_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'USED',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_mfa_recovery_code`
--

LOCK TABLES `un_plat_mfa_recovery_code` WRITE;
/*!40000 ALTER TABLE `un_plat_mfa_recovery_code` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_mfa_recovery_code` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_password_recovery_token`
--

DROP TABLE IF EXISTS `un_plat_password_recovery_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_password_recovery_token` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_at` datetime(3) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `consumed_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_password_recovery_token_hash` (`token_hash`),
  KEY `idx_plat_password_recovery_account` (`account_id`,`status`,`expires_at`),
  CONSTRAINT `fk_plat_password_recovery_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `ck_plat_password_recovery_expiry` CHECK ((`expires_at` > `requested_at`)),
  CONSTRAINT `ck_plat_password_recovery_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'USED',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_password_recovery_token`
--

LOCK TABLES `un_plat_password_recovery_token` WRITE;
/*!40000 ALTER TABLE `un_plat_password_recovery_token` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_password_recovery_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_permission`
--

DROP TABLE IF EXISTS `un_plat_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_permission` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_permission_code` (`scope_type`,`scope_key`,`permission_code`),
  UNIQUE KEY `uk_plat_permission_scope_id` (`scope_type`,`scope_key`,`id`),
  UNIQUE KEY `uk_plat_permission_system_id` (`system_id`,`id`),
  KEY `idx_plat_permission_system` (`system_id`,`status`),
  CONSTRAINT `fk_plat_permission_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_plat_permission_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null)))),
  CONSTRAINT `ck_plat_permission_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_permission`
--

LOCK TABLES `un_plat_permission` WRITE;
/*!40000 ALTER TABLE `un_plat_permission` DISABLE KEYS */;
INSERT INTO `un_plat_permission` VALUES (2085917351956254722,'PLATFORM',0,NULL,'platform.runtime.access','','SHELL','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917351994003457,'PLATFORM',0,NULL,'platform.admin.access','','SHELL','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352027557890,'PLATFORM',0,NULL,'platform.system.manage','','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352056918018,'PLATFORM',0,NULL,'platform.organization.manage','','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352090472450,'PLATFORM',0,NULL,'platform.role.manage','','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352140804097,'PLATFORM',0,NULL,'platform.permission.explain','预','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352182747138,'PLATFORM',0,NULL,'platform.ai.agent.use','Use platform AI Agent','MENU','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352224690178,'PLATFORM',0,NULL,'platform.ai.policy.manage','Manage platform AI Agent policy','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352258244609,'PLATFORM',0,NULL,'platform.task.read','Read personal platform tasks','DATA','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352287604738,'PLATFORM',0,NULL,'platform.task.create','Create personal platform tasks','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352316964866,'PLATFORM',0,NULL,'platform.task.manage','Manage personal platform tasks','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352346324993,'PLATFORM',0,NULL,'platform.openapi.application.manage','Manage platform OpenAPI applications','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352371490818,'PLATFORM',0,NULL,'platform.dashboard.manage','Manage platform dashboards','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352405045249,'PLATFORM',0,NULL,'platform.dashboard.view','View platform dashboard','MENU','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352430211074,'PLATFORM',0,NULL,'platform.flow.manage','Manage platform flows','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352459571201,'PLATFORM',0,NULL,'platform.flow.read','Read platform flows','MENU','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352488931329,'PLATFORM',0,NULL,'platform.flow.start','Start platform flows','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352535068673,'PLATFORM',0,NULL,'platform.audit.view','','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085917352585400321,'PLATFORM',0,NULL,'platform.settings.manage','','ACTION','ACTIVE','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085920203931418625,'SYSTEM',2085920203721703426,2085920203721703426,'module.config.manage','Manage module configuration','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920203994333186,'SYSTEM',2085920203721703426,2085920203721703426,'flow.external-task.work','Process external flow tasks','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204036276226,'SYSTEM',2085920203721703426,2085920203721703426,'work.project.access','Access work projects','MENU','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204057247746,'SYSTEM',2085920203721703426,2085920203721703426,'work.project.create','Create work projects','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204082413570,'SYSTEM',2085920203721703426,2085920203721703426,'work.project.manage','Manage all work projects','DATA','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204124356610,'SYSTEM',2085920203721703426,2085920203721703426,'work.report.access','Access daily work reports','MENU','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204162105345,'SYSTEM',2085920203721703426,2085920203721703426,'work.report.create','Create daily work reports','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204195659777,'SYSTEM',2085920203721703426,2085920203721703426,'work.report.manage','Manage team daily work reports','DATA','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204229214209,'SYSTEM',2085920203721703426,2085920203721703426,'system.runtime.access','','SHELL','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204258574338,'SYSTEM',2085920203721703426,2085920203721703426,'system.admin.access','','SHELL','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204279545858,'SYSTEM',2085920203721703426,2085920203721703426,'system.workbench.view','','MENU','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204308905985,'SYSTEM',2085920203721703426,2085920203721703426,'runtime.saved_view.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204334071810,'SYSTEM',2085920203721703426,2085920203721703426,'runtime.favorite.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204355043329,'SYSTEM',2085920203721703426,2085920203721703426,'ai.agent.use','使','MENU','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204401180673,'SYSTEM',2085920203721703426,2085920203721703426,'ai.policy.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204426346497,'SYSTEM',2085920203721703426,2085920203721703426,'system.settings.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204468289537,'SYSTEM',2085920203721703426,2085920203721703426,'system.settings.description.edit','','FIELD','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204485066754,'SYSTEM',2085920203721703426,2085920203721703426,'system.tenant.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204514426881,'SYSTEM',2085920203721703426,2085920203721703426,'system.organization.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204547981314,'SYSTEM',2085920203721703426,2085920203721703426,'system.member.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204568952833,'SYSTEM',2085920203721703426,2085920203721703426,'system.member.identity.view','','FIELD','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204602507265,'SYSTEM',2085920203721703426,2085920203721703426,'system.member.list','','DATA','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204627673090,'SYSTEM',2085920203721703426,2085920203721703426,'system.role.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204678004737,'SYSTEM',2085920203721703426,2085920203721703426,'system.permission.explain','预','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204707364865,'SYSTEM',2085920203721703426,2085920203721703426,'system.audit.view','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204740919297,'SYSTEM',2085920203721703426,2085920203721703426,'system.access.review','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204770279426,'SYSTEM',2085920203721703426,2085920203721703426,'openapi.application.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204808028161,'SYSTEM',2085920203721703426,2085920203721703426,'flow.definition.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204828999681,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.start','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204870942721,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.decide','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204896108546,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.read','','DATA','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204925468673,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.withdraw','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204959023106,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.terminate','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920204979994626,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.urge','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205030326274,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.comment','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205059686402,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.transfer','转','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205093240833,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.add-sign','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205122600962,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.return','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205164544002,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.claim','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205189709825,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.cancel-claim','取','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205223264257,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.reduce-sign','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205248430081,'SYSTEM',2085920203721703426,2085920203721703426,'flow.instance.copy','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205269401601,'SYSTEM',2085920203721703426,2085920203721703426,'work.task.access','','MENU','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205319733250,'SYSTEM',2085920203721703426,2085920203721703426,'work.task.create','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205336510465,'SYSTEM',2085920203721703426,2085920203721703426,'work.task.manage','','DATA','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205382647809,'SYSTEM',2085920203721703426,2085920203721703426,'event.message.access','','MENU','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205403619330,'SYSTEM',2085920203721703426,2085920203721703426,'event.template.manage','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205462339586,'SYSTEM',2085920203721703426,2085920203721703426,'file.create','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205487505409,'SYSTEM',2085920203721703426,2085920203721703426,'file.read','','DATA','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205521059841,'SYSTEM',2085920203721703426,2085920203721703426,'file.reference','','ACTION','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0),(2085920205542031361,'SYSTEM',2085920203721703426,2085920203721703426,'file.manage','','DATA','ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0);
/*!40000 ALTER TABLE `un_plat_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_quota`
--

DROP TABLE IF EXISTS `un_plat_quota`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_quota` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `quota_key` varchar(96) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `soft_limit` bigint DEFAULT NULL,
  `hard_limit` bigint NOT NULL,
  `used_value` bigint NOT NULL DEFAULT '0',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_quota_scope` (`system_id`,`tenant_key`,`quota_key`),
  KEY `idx_plat_quota_status` (`system_id`,`tenant_id`,`status`),
  CONSTRAINT `fk_plat_quota_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_quota_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_quota_scope` CHECK ((((`scope_type` = _utf8mb4'SYSTEM') and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'TENANT') and (`tenant_id` is not null)))),
  CONSTRAINT `ck_plat_quota_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_plat_quota_values` CHECK (((`hard_limit` >= 0) and (`used_value` >= 0) and ((`soft_limit` is null) or ((`soft_limit` >= 0) and (`soft_limit` <= `hard_limit`)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_quota`
--

LOCK TABLES `un_plat_quota` WRITE;
/*!40000 ALTER TABLE `un_plat_quota` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_quota` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_refresh_token`
--

DROP TABLE IF EXISTS `un_plat_refresh_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_refresh_token` (
  `id` bigint NOT NULL,
  `context_session_id` bigint NOT NULL,
  `token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_family` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `issued_at` datetime(3) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `used_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `rotated_to_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_refresh_token_hash` (`token_hash`),
  KEY `idx_plat_refresh_family` (`token_family`,`status`,`expires_at`),
  KEY `fk_plat_refresh_session` (`context_session_id`),
  KEY `fk_plat_refresh_rotated` (`rotated_to_id`),
  CONSTRAINT `fk_plat_refresh_rotated` FOREIGN KEY (`rotated_to_id`) REFERENCES `un_plat_refresh_token` (`id`),
  CONSTRAINT `fk_plat_refresh_session` FOREIGN KEY (`context_session_id`) REFERENCES `un_plat_context_session` (`id`),
  CONSTRAINT `ck_plat_refresh_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'USED',_utf8mb4'REVOKED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_refresh_token`
--

LOCK TABLES `un_plat_refresh_token` WRITE;
/*!40000 ALTER TABLE `un_plat_refresh_token` DISABLE KEYS */;
INSERT INTO `un_plat_refresh_token` VALUES (2085919780612898817,2085919780424155137,'ffcaf888443181d4e3762431bb5f444c40cc28e5c7eac770c18b17c738adb44d','932fa9f3-9429-40b0-9bea-605eb7488a31','REVOKED','2026-08-08 02:43:24.826','2026-08-15 02:43:24.826',NULL,'2026-08-08 02:45:28.507',NULL,'2026-08-08 02:43:24.826'),(2085920299221811202,2085920299205033986,'79d5af544380ce710c6bc95d33b13e9664700530ae9f59a005b3f5b5be5cd4db','618d6709-c41b-4e91-97d6-223dbf57e6ee','ACTIVE','2026-08-08 02:45:28.513','2026-08-15 02:45:28.513',NULL,NULL,NULL,'2026-08-08 02:45:28.513'),(2085923663923986434,2085923663814934529,'93e2fcbd1f2ad87dacbe921446703ca225cbc16d639d766c5669964dec4d5c33','3b3882b1-b6f3-4e45-acf5-d074e1d32500','REVOKED','2026-08-08 02:58:50.698','2026-08-15 02:58:50.698',NULL,'2026-08-08 02:59:19.627',NULL,'2026-08-08 02:58:50.698'),(2085923785244229634,2085923785227452418,'40f2e6b09d7b0d7a60a830df343a462469b69957e474c917fb155bbdd359c4a7','7dec6a15-ee25-4ec7-b820-24037e5bcb33','REVOKED','2026-08-08 02:59:19.646','2026-08-15 02:59:19.646',NULL,'2026-08-08 02:59:43.601',NULL,'2026-08-08 02:59:19.646'),(2085923885790085121,2085923885777502210,'69da1c0cdc87719b0229ff9a894790ca01090839f9cc20c1bf7a6f119339487a','52fb8564-0ba3-41fb-96d2-eff955d5e68a','REVOKED','2026-08-08 02:59:43.618','2026-08-15 02:59:43.618',NULL,'2026-08-08 02:59:45.683',NULL,'2026-08-08 02:59:43.618'),(2085923894468100097,2085923894447128577,'d174c4af569251191fd6bbd50dbd42627bab36512abf0b807f9921b460f9e470','7ec92295-fc2a-4a41-acd2-423d97d582a3','REVOKED','2026-08-08 02:59:45.686','2026-08-15 02:59:45.686',NULL,'2026-08-08 03:10:49.745',NULL,'2026-08-08 02:59:45.686'),(2085926679989604353,2085926679918301186,'f51d9e0f8557f2dfd7f262c38988fb78e59cdde91195fdce1c402877bffb3d8f','76109532-140e-4939-a1e5-73c0fbd3863f','REVOKED','2026-08-08 03:10:49.794','2026-08-15 03:10:49.794',NULL,'2026-08-08 03:10:52.020',NULL,'2026-08-08 03:10:49.794'),(2085926689279987713,2085926689271599105,'fb216b1220bf18891e8630de673179d540c8869fa96b6dc5ab3b6468149d8ffb','dd2f3213-5148-4a4d-bc51-c41ce3748cf6','REVOKED','2026-08-08 03:10:52.023','2026-08-15 03:10:52.023',NULL,'2026-08-08 03:11:52.403',NULL,'2026-08-08 03:10:52.023'),(2085926942615949314,2085926942603366401,'00a5844152882ce788c47c49860f3d98d926c7ba788900ec503cd8a2011922d8','fd75fab4-a19f-4f4a-bfea-70268bfc3971','REVOKED','2026-08-08 03:11:52.422','2026-08-15 03:11:52.422',NULL,'2026-08-08 03:11:54.565',NULL,'2026-08-08 03:11:52.422'),(2085926951604342785,2085926951595954178,'94ff7832879376959f2136ce530df169d0fedc0d27dc0964a8edc2f68220d033','a42c420e-802b-48e1-ab25-bb1f086cc7de','ACTIVE','2026-08-08 03:11:54.568','2026-08-15 03:11:54.568',NULL,NULL,NULL,'2026-08-08 03:11:54.568'),(2085928402820669441,2085928402673868802,'ff2114727f8506700050571808835fddb8dd9a841b04ca3f2f954a52e0d65054','9a5c3677-3af6-413f-94de-e0db1e06b68c','ACTIVE','2026-08-08 03:17:40.531','2026-08-15 03:17:40.531',NULL,NULL,NULL,'2026-08-08 03:17:40.531'),(2085928730202873858,2085928730194485249,'796f772391bc0fb34a53c68268c07b3af0b3dd05203907795b33ab7874ef0408','b77dc814-25d8-4359-a464-2489b110da1b','REVOKED','2026-08-08 03:18:58.618','2026-08-15 03:18:58.618',NULL,'2026-08-08 03:19:22.552',NULL,'2026-08-08 03:18:58.618'),(2085928830660648962,2085928830627094530,'4842cb3930ab60bf5528fd415f81e4864982b3229c1f86b8883e821054cfaa32','00a104a5-4c14-4e57-b32c-0ee60971024f','REVOKED','2026-08-08 03:19:22.562','2026-08-15 03:19:22.562',NULL,'2026-08-08 03:19:42.305',NULL,'2026-08-08 03:19:22.562'),(2085928913519124482,2085928913506541569,'7d9bd6ade8f2a0dde4cbb7e7198a600e4937c3dcd9550205f1aace419db5b08c','2f1b6595-03d8-4685-afb2-c44cc5b40f10','REVOKED','2026-08-08 03:19:42.323','2026-08-15 03:19:42.323',NULL,'2026-08-08 03:19:44.762',NULL,'2026-08-08 03:19:42.323'),(2085928923765809153,2085928923757420545,'92e4d54e312e963e478e57af3b9c454444f3703e98c56eeab938e8cfcb856c8b','53981ab9-38e7-42e0-bda8-4a1394375089','REVOKED','2026-08-08 03:19:44.766','2026-08-15 03:19:44.766',NULL,'2026-08-08 03:20:00.140',NULL,'2026-08-08 03:19:44.766'),(2085928988324536321,2085928988303564801,'fb2ad7e89fb02068b64be7827ec0a2e6ed7ba68a0634750bd6f94229744a8dfd','b00424aa-e023-4c39-b960-7aacb662cb95','REVOKED','2026-08-08 03:20:00.156','2026-08-15 03:20:00.156',NULL,'2026-08-08 03:20:02.011',NULL,'2026-08-08 03:20:00.156'),(2085928996130136065,2085928996100775938,'d295afba32ae2f474f621f3cc9127d6cb235f5ba64c5292f7f6822afe9a1ec59','67ecbb66-62e4-4de3-90a2-34d659a75e7e','ACTIVE','2026-08-08 03:20:02.015','2026-08-15 03:20:02.015',NULL,NULL,NULL,'2026-08-08 03:20:02.015'),(2085930015535149057,2085930015484817409,'ec3cc4d3bb916a2f6ea2d69fe2e3138f53d842c4d3096ea5b1a464e0baabec9a','b5a0e21e-a30f-4ab3-acbc-0638b65e879f','REVOKED','2026-08-08 03:24:05.054','2026-08-15 03:24:05.054',NULL,'2026-08-08 03:24:32.830',NULL,'2026-08-08 03:24:05.054'),(2085930132040331266,2085930132010971138,'fb2d34c3e6978702de5f82d78a388231ce9435aa43cfca9a4780a684a939063a','28d8bb33-b4a4-4b90-865b-d99a8f95dc44','REVOKED','2026-08-08 03:24:32.837','2026-08-15 03:24:32.837',NULL,'2026-08-08 03:27:51.190',NULL,'2026-08-08 03:24:32.837'),(2085930271270252546,2085930271253475330,'c94a944fb0741cb0216e276bb5737d9e384b43808f0f895a3081b41d06f1add4','834360cb-8ce5-4e29-9ba1-f7580bbe1a78','REVOKED','2026-08-08 03:25:06.035','2026-08-15 03:25:06.035',NULL,'2026-08-08 03:25:40.575',NULL,'2026-08-08 03:25:06.035'),(2085930416141512705,2085930416137318402,'36a4be1428651030c2a0d56e360f6e512409c02cbe48b4805f65adc4aab66023','c7093874-0f5b-46ba-bdc8-7b37d9748d7b','ACTIVE','2026-08-08 03:25:40.578','2026-08-15 03:25:40.578',NULL,NULL,NULL,'2026-08-08 03:25:40.578'),(2085930964064415745,2085930964043444225,'1c94b75c538999a0c5639052a1f514bbdd79d5bafdf9afe93dcdaad59748fc5d','8f6cb52a-9146-46a3-9010-40270be898d3','REVOKED','2026-08-08 03:27:51.208','2026-08-15 03:27:51.208',NULL,'2026-08-08 03:28:04.016',NULL,'2026-08-08 03:27:51.208'),(2085931017793449985,2085931017776672769,'28221fbeaa7eb7d026e9ee9d8fb03bfbd3c38fcdff63f25facc2c2905c23d99a','40dd2b93-9e1d-4058-bf33-6fc1d5be0755','ACTIVE','2026-08-08 03:28:04.020','2026-08-15 03:28:04.020',NULL,NULL,NULL,'2026-08-08 03:28:04.020');
/*!40000 ALTER TABLE `un_plat_refresh_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_role`
--

DROP TABLE IF EXISTS `un_plat_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_role` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_version` bigint NOT NULL DEFAULT '1',
  `data_scope_id` bigint DEFAULT NULL,
  `is_builtin` tinyint(1) NOT NULL DEFAULT '0',
  `published_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_code` (`scope_type`,`scope_key`,`role_code`),
  UNIQUE KEY `uk_plat_role_scope_id` (`scope_type`,`scope_key`,`id`),
  UNIQUE KEY `uk_plat_role_scope_tenant_id` (`scope_type`,`scope_key`,`tenant_key`,`id`),
  UNIQUE KEY `uk_plat_role_system_id` (`system_id`,`id`),
  KEY `idx_plat_role_system` (`system_id`,`tenant_id`,`status`),
  KEY `fk_plat_role_tenant` (`tenant_id`),
  KEY `fk_plat_role_active_data_scope` (`scope_type`,`scope_key`,`tenant_key`,`data_scope_id`),
  CONSTRAINT `fk_plat_role_active_data_scope` FOREIGN KEY (`scope_type`, `scope_key`, `tenant_key`, `data_scope_id`) REFERENCES `un_plat_data_scope` (`scope_type`, `scope_key`, `tenant_key`, `id`),
  CONSTRAINT `fk_plat_role_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_plat_role_system_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_role_builtin` CHECK ((`is_builtin` in (0,1))),
  CONSTRAINT `ck_plat_role_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null)))),
  CONSTRAINT `ck_plat_role_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_plat_role_type` CHECK ((`role_type` in (_utf8mb4'ROOT',_utf8mb4'ADMIN',_utf8mb4'MEMBER',_utf8mb4'CUSTOM'))),
  CONSTRAINT `ck_plat_role_versions` CHECK (((`permission_version` > 0) and (`published_version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_role`
--

LOCK TABLES `un_plat_role` WRITE;
/*!40000 ALTER TABLE `un_plat_role` DISABLE KEYS */;
INSERT INTO `un_plat_role` (`id`, `scope_type`, `scope_key`, `system_id`, `tenant_id`, `role_code`, `name`, `role_type`, `status`, `permission_version`, `data_scope_id`, `is_builtin`, `published_version`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `version`) VALUES (2085917351893340161,'PLATFORM',0,NULL,NULL,'platform_root','平台','ROOT','ACTIVE',1,2085917351893340162,1,1,'2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,NULL,0),(2085920203797200900,'SYSTEM',2085920203721703426,2085920203721703426,NULL,'system_owner','系统','ROOT','ACTIVE',1,2085920203864309761,1,1,'2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,NULL,0);
/*!40000 ALTER TABLE `un_plat_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_role_draft`
--

DROP TABLE IF EXISTS `un_plat_role_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_role_draft` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `role_id` bigint NOT NULL,
  `draft_version` bigint NOT NULL,
  `base_published_version` bigint NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `draft_json` json NOT NULL,
  `check_result_json` json DEFAULT NULL,
  `checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `checked_at` datetime(3) DEFAULT NULL,
  `checked_by` bigint DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_draft_role` (`role_id`),
  UNIQUE KEY `uk_plat_role_draft_scope_role` (`scope_type`,`scope_key`,`role_id`),
  KEY `idx_plat_role_draft_state` (`scope_type`,`scope_key`,`status`,`updated_at`),
  CONSTRAINT `fk_plat_role_draft_role` FOREIGN KEY (`scope_type`, `scope_key`, `role_id`) REFERENCES `un_plat_role` (`scope_type`, `scope_key`, `id`),
  CONSTRAINT `ck_plat_role_draft_document` CHECK ((json_type(`draft_json`) = _utf8mb4'OBJECT')),
  CONSTRAINT `ck_plat_role_draft_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null)))),
  CONSTRAINT `ck_plat_role_draft_state` CHECK ((((`status` = _utf8mb4'DRAFT') and (`checked_at` is null) and (`checked_by` is null) and (`published_at` is null) and (`published_by` is null)) or ((`status` = _utf8mb4'CHECKED') and (`checked_at` is not null) and (`checked_by` is not null) and (`checksum` is not null) and (`published_at` is null) and (`published_by` is null)) or ((`status` = _utf8mb4'PUBLISHED') and (`checked_at` is not null) and (`checked_by` is not null) and (`checksum` is not null) and (`published_at` is not null) and (`published_by` is not null)))),
  CONSTRAINT `ck_plat_role_draft_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'CHECKED',_utf8mb4'PUBLISHED'))),
  CONSTRAINT `ck_plat_role_draft_versions` CHECK (((`draft_version` > 0) and (`base_published_version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_role_draft`
--

LOCK TABLES `un_plat_role_draft` WRITE;
/*!40000 ALTER TABLE `un_plat_role_draft` DISABLE KEYS */;
INSERT INTO `un_plat_role_draft` VALUES (2085917352774144002,'PLATFORM',0,NULL,2085917351893340161,1,1,'PUBLISHED','{\"roleId\": \"2085917351893340161\", \"roleCode\": \"platform_root\", \"scopeKey\": \"0\", \"dataScope\": {\"id\": \"2085917351893340162\", \"kind\": \"ALL\"}, \"scopeType\": \"PLATFORM\", \"permissions\": [{\"code\": \"platform.runtime.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917351956254722\"}, {\"code\": \"platform.admin.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917351994003457\"}, {\"code\": \"platform.system.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352027557890\"}, {\"code\": \"platform.organization.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352056918018\"}, {\"code\": \"platform.role.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352090472450\"}, {\"code\": \"platform.permission.explain\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352140804097\"}, {\"code\": \"platform.ai.agent.use\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352182747138\"}, {\"code\": \"platform.ai.policy.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352224690178\"}, {\"code\": \"platform.task.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352258244609\"}, {\"code\": \"platform.task.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352287604738\"}, {\"code\": \"platform.task.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352316964866\"}, {\"code\": \"platform.openapi.application.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352346324993\"}, {\"code\": \"platform.dashboard.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352371490818\"}, {\"code\": \"platform.dashboard.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352405045249\"}, {\"code\": \"platform.flow.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352430211074\"}, {\"code\": \"platform.flow.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352459571201\"}, {\"code\": \"platform.flow.start\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352488931329\"}, {\"code\": \"platform.audit.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352535068673\"}, {\"code\": \"platform.settings.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085917352585400321\"}]}','{\"valid\": true, \"source\": \"INITIAL_PROVISION\"}','a272e25781bc638b03c3d0f9ae0f15b99a84fa2248e3f5d312576c356d9e3637','2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,'2026-08-08 02:33:45.508',2085917350597300225,0),(2085920205604945921,'SYSTEM',2085920203721703426,2085920203721703426,2085920203797200900,1,1,'PUBLISHED','{\"roleId\": \"2085920203797200900\", \"roleCode\": \"system_owner\", \"scopeKey\": \"2085920203721703426\", \"dataScope\": {\"id\": \"2085920203864309761\", \"kind\": \"ALL\"}, \"scopeType\": \"SYSTEM\", \"permissions\": [{\"code\": \"module.config.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920203931418625\"}, {\"code\": \"flow.external-task.work\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920203994333186\"}, {\"code\": \"work.project.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204036276226\"}, {\"code\": \"work.project.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204057247746\"}, {\"code\": \"work.project.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204082413570\"}, {\"code\": \"work.report.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204124356610\"}, {\"code\": \"work.report.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204162105345\"}, {\"code\": \"work.report.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204195659777\"}, {\"code\": \"system.runtime.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204229214209\"}, {\"code\": \"system.admin.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204258574338\"}, {\"code\": \"system.workbench.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204279545858\"}, {\"code\": \"runtime.saved_view.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204308905985\"}, {\"code\": \"runtime.favorite.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204334071810\"}, {\"code\": \"ai.agent.use\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204355043329\"}, {\"code\": \"ai.policy.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204401180673\"}, {\"code\": \"system.settings.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204426346497\"}, {\"code\": \"system.settings.description.edit\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204468289537\"}, {\"code\": \"system.tenant.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204485066754\"}, {\"code\": \"system.organization.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204514426881\"}, {\"code\": \"system.member.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204547981314\"}, {\"code\": \"system.member.identity.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204568952833\"}, {\"code\": \"system.member.list\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204602507265\"}, {\"code\": \"system.role.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204627673090\"}, {\"code\": \"system.permission.explain\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204678004737\"}, {\"code\": \"system.audit.view\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204707364865\"}, {\"code\": \"system.access.review\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204740919297\"}, {\"code\": \"openapi.application.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204770279426\"}, {\"code\": \"flow.definition.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204808028161\"}, {\"code\": \"flow.instance.start\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204828999681\"}, {\"code\": \"flow.instance.decide\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204870942721\"}, {\"code\": \"flow.instance.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204896108546\"}, {\"code\": \"flow.instance.withdraw\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204925468673\"}, {\"code\": \"flow.instance.terminate\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204959023106\"}, {\"code\": \"flow.instance.urge\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920204979994626\"}, {\"code\": \"flow.instance.comment\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205030326274\"}, {\"code\": \"flow.instance.transfer\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205059686402\"}, {\"code\": \"flow.instance.add-sign\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205093240833\"}, {\"code\": \"flow.instance.return\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205122600962\"}, {\"code\": \"flow.instance.claim\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205164544002\"}, {\"code\": \"flow.instance.cancel-claim\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205189709825\"}, {\"code\": \"flow.instance.reduce-sign\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205223264257\"}, {\"code\": \"flow.instance.copy\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205248430081\"}, {\"code\": \"work.task.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205269401601\"}, {\"code\": \"work.task.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205319733250\"}, {\"code\": \"work.task.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205336510465\"}, {\"code\": \"event.message.access\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205382647809\"}, {\"code\": \"event.template.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205403619330\"}, {\"code\": \"file.create\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205462339586\"}, {\"code\": \"file.read\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205487505409\"}, {\"code\": \"file.reference\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205521059841\"}, {\"code\": \"file.manage\", \"effect\": \"ALLOW\", \"permissionId\": \"2085920205542031361\"}]}','{\"valid\": true, \"source\": \"INITIAL_PROVISION\"}','6929542166f2c32bb56cf47535d4191ec0c7359e03973f1eb7e67576f233fd0b','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,0);
/*!40000 ALTER TABLE `un_plat_role_draft` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_role_permission`
--

DROP TABLE IF EXISTS `un_plat_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_role_permission` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `effect` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_permission` (`role_id`,`permission_id`),
  KEY `fk_plat_role_permission_permission` (`permission_id`),
  KEY `fk_plat_role_permission_scoped_role` (`scope_type`,`scope_key`,`role_id`),
  KEY `fk_plat_role_permission_scoped_permission` (`scope_type`,`scope_key`,`permission_id`),
  CONSTRAINT `fk_plat_role_permission_scoped_permission` FOREIGN KEY (`scope_type`, `scope_key`, `permission_id`) REFERENCES `un_plat_permission` (`scope_type`, `scope_key`, `id`),
  CONSTRAINT `fk_plat_role_permission_scoped_role` FOREIGN KEY (`scope_type`, `scope_key`, `role_id`) REFERENCES `un_plat_role` (`scope_type`, `scope_key`, `id`),
  CONSTRAINT `ck_plat_role_permission_effect` CHECK ((`effect` in (_utf8mb4'ALLOW',_utf8mb4'DENY'))),
  CONSTRAINT `ck_plat_role_permission_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_role_permission`
--

LOCK TABLES `un_plat_role_permission` WRITE;
/*!40000 ALTER TABLE `un_plat_role_permission` DISABLE KEYS */;
INSERT INTO `un_plat_role_permission` VALUES (2085917351968837634,'PLATFORM',0,2085917351893340161,2085917351956254722,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352006586370,'PLATFORM',0,2085917351893340161,2085917351994003457,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352040140802,'PLATFORM',0,2085917351893340161,2085917352027557890,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352065306626,'PLATFORM',0,2085917351893340161,2085917352056918018,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352098861057,'PLATFORM',0,2085917351893340161,2085917352090472450,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352157581314,'PLATFORM',0,2085917351893340161,2085917352140804097,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352195330050,'PLATFORM',0,2085917351893340161,2085917352182747138,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352237273090,'PLATFORM',0,2085917351893340161,2085917352224690178,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352266633217,'PLATFORM',0,2085917351893340161,2085917352258244609,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352295993345,'PLATFORM',0,2085917351893340161,2085917352287604738,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352325353473,'PLATFORM',0,2085917351893340161,2085917352316964866,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352350519298,'PLATFORM',0,2085917351893340161,2085917352346324993,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352379879426,'PLATFORM',0,2085917351893340161,2085917352371490818,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352413433858,'PLATFORM',0,2085917351893340161,2085917352405045249,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352438599682,'PLATFORM',0,2085917351893340161,2085917352430211074,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352467959810,'PLATFORM',0,2085917351893340161,2085917352459571201,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352501514242,'PLATFORM',0,2085917351893340161,2085917352488931329,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352556040194,'PLATFORM',0,2085917351893340161,2085917352535068673,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085917352610566146,'PLATFORM',0,2085917351893340161,2085917352585400321,'ALLOW','2026-08-08 02:33:45.508',2085917350597300225),(2085920203952390145,'SYSTEM',2085920203721703426,2085920203797200900,2085920203931418625,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204002721794,'SYSTEM',2085920203721703426,2085920203797200900,2085920203994333186,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204040470529,'SYSTEM',2085920203721703426,2085920203797200900,2085920204036276226,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204065636354,'SYSTEM',2085920203721703426,2085920203797200900,2085920204057247746,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204094996482,'SYSTEM',2085920203721703426,2085920203797200900,2085920204082413570,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204132745217,'SYSTEM',2085920203721703426,2085920203797200900,2085920204124356610,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204174688257,'SYSTEM',2085920203721703426,2085920203797200900,2085920204162105345,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204204048386,'SYSTEM',2085920203721703426,2085920203797200900,2085920204195659777,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204237602817,'SYSTEM',2085920203721703426,2085920203797200900,2085920204229214209,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204262768641,'SYSTEM',2085920203721703426,2085920203797200900,2085920204258574338,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204283740161,'SYSTEM',2085920203721703426,2085920203797200900,2085920204279545858,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204317294593,'SYSTEM',2085920203721703426,2085920203797200900,2085920204308905985,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204338266113,'SYSTEM',2085920203721703426,2085920203797200900,2085920204334071810,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204380209153,'SYSTEM',2085920203721703426,2085920203797200900,2085920204355043329,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204409569282,'SYSTEM',2085920203721703426,2085920203797200900,2085920204401180673,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204443123713,'SYSTEM',2085920203721703426,2085920203797200900,2085920204426346497,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204472483842,'SYSTEM',2085920203721703426,2085920203797200900,2085920204468289537,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204493455361,'SYSTEM',2085920203721703426,2085920203797200900,2085920204485066754,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204531204097,'SYSTEM',2085920203721703426,2085920203797200900,2085920204514426881,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204552175618,'SYSTEM',2085920203721703426,2085920203797200900,2085920204547981314,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204581535746,'SYSTEM',2085920203721703426,2085920203797200900,2085920204568952833,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204610895873,'SYSTEM',2085920203721703426,2085920203797200900,2085920204602507265,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204631867394,'SYSTEM',2085920203721703426,2085920203797200900,2085920204627673090,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204686393346,'SYSTEM',2085920203721703426,2085920203797200900,2085920204678004737,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204719947778,'SYSTEM',2085920203721703426,2085920203797200900,2085920204707364865,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204753502209,'SYSTEM',2085920203721703426,2085920203797200900,2085920204740919297,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204782862337,'SYSTEM',2085920203721703426,2085920203797200900,2085920204770279426,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204812222465,'SYSTEM',2085920203721703426,2085920203797200900,2085920204808028161,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204841582594,'SYSTEM',2085920203721703426,2085920203797200900,2085920204828999681,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204879331329,'SYSTEM',2085920203721703426,2085920203797200900,2085920204870942721,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204900302850,'SYSTEM',2085920203721703426,2085920203797200900,2085920204896108546,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204938051585,'SYSTEM',2085920203721703426,2085920203797200900,2085920204925468673,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204967411714,'SYSTEM',2085920203721703426,2085920203797200900,2085920204959023106,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920204992577537,'SYSTEM',2085920203721703426,2085920203797200900,2085920204979994626,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205038714882,'SYSTEM',2085920203721703426,2085920203797200900,2085920205030326274,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205068075009,'SYSTEM',2085920203721703426,2085920203797200900,2085920205059686402,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205101629442,'SYSTEM',2085920203721703426,2085920203797200900,2085920205093240833,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205139378178,'SYSTEM',2085920203721703426,2085920203797200900,2085920205122600962,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205172932609,'SYSTEM',2085920203721703426,2085920203797200900,2085920205164544002,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205198098434,'SYSTEM',2085920203721703426,2085920203797200900,2085920205189709825,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205227458561,'SYSTEM',2085920203721703426,2085920203797200900,2085920205223264257,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205252624385,'SYSTEM',2085920203721703426,2085920203797200900,2085920205248430081,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205294567425,'SYSTEM',2085920203721703426,2085920203797200900,2085920205269401601,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205328121858,'SYSTEM',2085920203721703426,2085920203797200900,2085920205319733250,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205361676290,'SYSTEM',2085920203721703426,2085920203797200900,2085920205336510465,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205391036417,'SYSTEM',2085920203721703426,2085920203797200900,2085920205382647809,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205441368065,'SYSTEM',2085920203721703426,2085920203797200900,2085920205403619330,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205466533890,'SYSTEM',2085920203721703426,2085920203797200900,2085920205462339586,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205500088321,'SYSTEM',2085920203721703426,2085920203797200900,2085920205487505409,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205529448450,'SYSTEM',2085920203721703426,2085920203797200900,2085920205521059841,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225),(2085920205558808577,'SYSTEM',2085920203721703426,2085920203797200900,2085920205542031361,'ALLOW','2026-08-08 02:45:05.748',2085917350597300225);
/*!40000 ALTER TABLE `un_plat_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_system`
--

DROP TABLE IF EXISTS `un_plat_system`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_system` (
  `id` bigint NOT NULL,
  `system_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `tenant_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_account_id` bigint NOT NULL,
  `permission_version` bigint NOT NULL DEFAULT '1',
  `initialized_at` datetime(3) DEFAULT NULL,
  `init_failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `init_failed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `archived_at` datetime(3) DEFAULT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `tombstone_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_system_code` (`system_code`),
  UNIQUE KEY `uk_plat_system_id_owner` (`id`,`owner_account_id`),
  KEY `idx_plat_system_owner` (`owner_account_id`,`status`),
  KEY `fk_plat_system_deleted_by` (`deleted_by`),
  CONSTRAINT `fk_plat_system_deleted_by` FOREIGN KEY (`deleted_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_system_owner` FOREIGN KEY (`owner_account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `ck_plat_system_initialization_failure` CHECK ((((`status` = _utf8mb4'INIT_FAILED') and (`init_failure_code` is not null) and (`init_failed_at` is not null)) or ((`status` <> _utf8mb4'INIT_FAILED') and (`init_failure_code` is null) and (`init_failed_at` is null)))),
  CONSTRAINT `ck_plat_system_permission_version` CHECK ((`permission_version` > 0)),
  CONSTRAINT `ck_plat_system_status` CHECK ((`status` in (_utf8mb4'INITIALIZING',_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED',_utf8mb4'INIT_FAILED'))),
  CONSTRAINT `ck_plat_system_tenant_mode` CHECK ((`tenant_mode` in (_utf8mb4'SINGLE',_utf8mb4'MULTI'))),
  CONSTRAINT `ck_plat_system_tombstone` CHECK ((((`deleted_at` is null) and (`deleted_by` is null) and (`tombstone_reason` is null)) or ((`deleted_at` is not null) and (`deleted_by` is not null) and (`tombstone_reason` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_system`
--

LOCK TABLES `un_plat_system` WRITE;
/*!40000 ALTER TABLE `un_plat_system` DISABLE KEYS */;
INSERT INTO `un_plat_system` VALUES (2085920203721703426,'cycle118_acceptance','Cycle118 ','','ACTIVE','SINGLE',2085917350597300225,1,'2026-08-08 02:45:05.748',NULL,NULL,'2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,NULL,NULL,NULL,NULL,1);
/*!40000 ALTER TABLE `un_plat_system` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_system_delete_request`
--

DROP TABLE IF EXISTS `un_plat_system_delete_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_system_delete_request` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `system_version` bigint NOT NULL,
  `dependency_json` json NOT NULL,
  `blocker_json` json NOT NULL,
  `impact_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmation_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `consumed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `active_system_id` bigint GENERATED ALWAYS AS ((case when (`status` = _ascii'PREVIEWED') then `system_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_system_delete_active` (`active_system_id`),
  KEY `idx_plat_system_delete_expiry` (`status`,`expires_at`),
  KEY `fk_plat_system_delete_system` (`system_id`),
  KEY `fk_plat_system_delete_actor` (`created_by`),
  CONSTRAINT `fk_plat_system_delete_actor` FOREIGN KEY (`created_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_system_delete_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_plat_system_delete_hashes` CHECK ((regexp_like(`impact_fingerprint`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`confirmation_token_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_plat_system_delete_state` CHECK ((((`status` = _utf8mb4'CONSUMED') and (`consumed_at` is not null)) or ((`status` <> _utf8mb4'CONSUMED') and (`consumed_at` is null)))),
  CONSTRAINT `ck_plat_system_delete_status` CHECK ((`status` in (_utf8mb4'PREVIEWED',_utf8mb4'CONSUMED',_utf8mb4'BLOCKED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_system_delete_request`
--

LOCK TABLES `un_plat_system_delete_request` WRITE;
/*!40000 ALTER TABLE `un_plat_system_delete_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_system_delete_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_system_identity_policy`
--

DROP TABLE IF EXISTS `un_plat_system_identity_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_system_identity_policy` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `allowed_domains_json` json NOT NULL,
  `jit_system_member` tinyint(1) NOT NULL DEFAULT '0',
  `unmatched_action` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'REQUIRE_REVIEW',
  `schedule_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `schedule_interval_minutes` int DEFAULT NULL,
  `next_sync_at` datetime(3) DEFAULT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
  `last_confirmed_snapshot_id` bigint DEFAULT NULL,
  `last_sync_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_system_identity_policy_scope` (`system_id`,`tenant_id`,`provider_id`),
  KEY `idx_plat_system_identity_policy_schedule` (`status`,`schedule_enabled`,`next_sync_at`),
  KEY `fk_plat_system_identity_policy_provider` (`provider_id`),
  CONSTRAINT `fk_plat_system_identity_policy_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_plat_identity_provider` (`id`),
  CONSTRAINT `fk_plat_system_identity_policy_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_system_identity_policy_schedule` CHECK ((((`schedule_enabled` = false) and (`schedule_interval_minutes` is null) and (`next_sync_at` is null)) or ((`schedule_enabled` = true) and (`schedule_interval_minutes` between 15 and 10080) and (`next_sync_at` is not null)))),
  CONSTRAINT `ck_plat_system_identity_policy_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_plat_system_identity_policy_unmatched` CHECK ((`unmatched_action` in (_utf8mb4'REQUIRE_REVIEW',_utf8mb4'SKIP')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_system_identity_policy`
--

LOCK TABLES `un_plat_system_identity_policy` WRITE;
/*!40000 ALTER TABLE `un_plat_system_identity_policy` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_system_identity_policy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_system_setting`
--

DROP TABLE IF EXISTS `un_plat_system_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_system_setting` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `setting_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value_kind` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value_json` json DEFAULT NULL,
  `secret_ref` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_system_setting_key` (`system_id`,`setting_key`),
  KEY `idx_plat_system_setting_status` (`system_id`,`status`,`updated_at`),
  CONSTRAINT `fk_plat_system_setting_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_plat_system_setting_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_plat_system_setting_value` CHECK ((((`value_kind` = _utf8mb4'JSON') and (`value_json` is not null) and (`secret_ref` is null)) or ((`value_kind` = _utf8mb4'SECRET_REF') and (`value_json` is null) and (`secret_ref` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_system_setting`
--

LOCK TABLES `un_plat_system_setting` WRITE;
/*!40000 ALTER TABLE `un_plat_system_setting` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_system_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_tenant`
--

DROP TABLE IF EXISTS `un_plat_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_tenant` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `active_default_system_id` bigint GENERATED ALWAYS AS ((case when ((`is_default` = 1) and (`deleted_at` is null)) then `system_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_tenant_code` (`system_id`,`tenant_code`),
  UNIQUE KEY `uk_plat_tenant_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_plat_tenant_one_active_default` (`active_default_system_id`),
  KEY `idx_plat_tenant_default` (`system_id`,`is_default`,`status`),
  CONSTRAINT `fk_plat_tenant_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `ck_plat_tenant_default` CHECK ((`is_default` in (0,1))),
  CONSTRAINT `ck_plat_tenant_default_state` CHECK (((`is_default` = 0) or ((`status` = _utf8mb4'ACTIVE') and (`deleted_at` is null)))),
  CONSTRAINT `ck_plat_tenant_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_tenant`
--

LOCK TABLES `un_plat_tenant` WRITE;
/*!40000 ALTER TABLE `un_plat_tenant` DISABLE KEYS */;
INSERT INTO `un_plat_tenant` (`id`, `system_id`, `tenant_code`, `name`, `is_default`, `status`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `version`) VALUES (2085920203797200898,2085920203721703426,'default','Cycle118 ',1,'ACTIVE','2026-08-08 02:45:05.748',2085917350597300225,'2026-08-08 02:45:05.748',2085917350597300225,NULL,0);
/*!40000 ALTER TABLE `un_plat_tenant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_tenant_domain`
--

DROP TABLE IF EXISTS `un_plat_tenant_domain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_tenant_domain` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `domain_name` varchar(253) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `domain_normalized` varchar(253) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS (lower(trim(`domain_name`))) STORED,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `active_primary_tenant_id` bigint GENERATED ALWAYS AS ((case when ((`is_primary` = 1) and (`status` = _utf8mb4'VERIFIED') and (`deleted_at` is null)) then `tenant_id` else NULL end)) STORED,
  `verification_token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verified_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_tenant_domain_name` (`domain_normalized`),
  UNIQUE KEY `uk_plat_tenant_domain_primary` (`active_primary_tenant_id`),
  KEY `idx_plat_tenant_domain_tenant` (`system_id`,`tenant_id`,`status`),
  CONSTRAINT `fk_plat_tenant_domain_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_tenant_domain_name` CHECK (((char_length(trim(`domain_name`)) between 1 and 253) and (not((trim(`domain_name`) like _utf8mb4'% %'))))),
  CONSTRAINT `ck_plat_tenant_domain_primary` CHECK ((`is_primary` in (0,1))),
  CONSTRAINT `ck_plat_tenant_domain_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'VERIFIED',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_plat_tenant_domain_verified` CHECK ((((`status` = _utf8mb4'VERIFIED') and (`verified_at` is not null)) or ((`status` <> _utf8mb4'VERIFIED') and (`verified_at` is null) and (`is_primary` = 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_tenant_domain`
--

LOCK TABLES `un_plat_tenant_domain` WRITE;
/*!40000 ALTER TABLE `un_plat_tenant_domain` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_tenant_domain` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_plat_tenant_lifecycle_operation`
--

DROP TABLE IF EXISTS `un_plat_tenant_lifecycle_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_plat_tenant_lifecycle_operation` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `source_tenant_id` bigint NOT NULL,
  `target_tenant_id` bigint DEFAULT NULL,
  `plan_operation_id` bigint DEFAULT NULL,
  `operation_type` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `snapshot_json` json NOT NULL,
  `result_json` json DEFAULT NULL,
  `snapshot_checksum` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmation_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `consumed_at` datetime(3) DEFAULT NULL,
  `payload_ciphertext` longblob,
  `payload_ciphertext_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `payload_plaintext_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `encryption_key_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `encryption_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `payload_schema_version` int NOT NULL DEFAULT '1',
  `database_migration_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload_row_count` bigint NOT NULL DEFAULT '0',
  `payload_size_bytes` bigint NOT NULL DEFAULT '0',
  `requested_at` datetime(3) NOT NULL,
  `started_at` datetime(3) NOT NULL,
  `finished_at` datetime(3) NOT NULL,
  `requested_by` bigint NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_plat_tenant_lifecycle_source` (`system_id`,`source_tenant_id`,`operation_type`,`requested_at`),
  KEY `idx_plat_tenant_lifecycle_target` (`system_id`,`target_tenant_id`,`operation_type`,`requested_at`),
  KEY `idx_plat_tenant_lifecycle_plan` (`plan_operation_id`),
  KEY `idx_plat_tenant_lifecycle_expiry` (`status`,`expires_at`),
  KEY `fk_plat_tenant_lifecycle_actor` (`requested_by`),
  CONSTRAINT `fk_plat_tenant_lifecycle_actor` FOREIGN KEY (`requested_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_plat_tenant_lifecycle_plan` FOREIGN KEY (`plan_operation_id`) REFERENCES `un_plat_tenant_lifecycle_operation` (`id`),
  CONSTRAINT `fk_plat_tenant_lifecycle_source` FOREIGN KEY (`system_id`, `source_tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_plat_tenant_lifecycle_target` FOREIGN KEY (`system_id`, `target_tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_plat_tenant_lifecycle_checksum` CHECK ((regexp_like(`snapshot_checksum`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`plan_fingerprint`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_plat_tenant_lifecycle_execution` CHECK ((((`operation_type` = _utf8mb4'BACKUP') and (`status` = _utf8mb4'SUCCEEDED') and (`plan_operation_id` is null)) or ((`operation_type` in (_utf8mb4'MIGRATION_PREVIEW',_utf8mb4'RECOVERY_PREVIEW')) and (`plan_operation_id` is null)) or ((`operation_type` in (_utf8mb4'MIGRATION',_utf8mb4'RECOVERY')) and (`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')) and (`plan_operation_id` is not null)))),
  CONSTRAINT `ck_plat_tenant_lifecycle_payload` CHECK ((((`operation_type` = _utf8mb4'BACKUP') and (`payload_ciphertext` is not null) and regexp_like(`payload_ciphertext_sha256`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`payload_plaintext_sha256`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`encryption_key_ref`,_utf8mb4'^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$') and regexp_like(`encryption_key_version`,_utf8mb4'^[0-9a-f]{16}$') and (`payload_schema_version` = 1) and (`payload_row_count` >= 0) and (`payload_size_bytes` > 0)) or ((`operation_type` <> _utf8mb4'BACKUP') and (`payload_ciphertext` is null) and (`payload_ciphertext_sha256` is null) and (`payload_plaintext_sha256` is null) and (`encryption_key_ref` is null) and (`encryption_key_version` is null) and (`payload_schema_version` = 1) and (`payload_row_count` >= 0) and (`payload_size_bytes` >= 0)))),
  CONSTRAINT `ck_plat_tenant_lifecycle_preview` CHECK ((((`operation_type` in (_utf8mb4'MIGRATION_PREVIEW',_utf8mb4'RECOVERY_PREVIEW')) and (`status` in (_utf8mb4'PREVIEWED',_utf8mb4'BLOCKED',_utf8mb4'CONSUMED',_utf8mb4'EXPIRED')) and (`expires_at` is not null) and (((`status` in (_utf8mb4'PREVIEWED',_utf8mb4'CONSUMED')) and (`confirmation_token_hash` is not null)) or (`status` in (_utf8mb4'BLOCKED',_utf8mb4'EXPIRED'))) and (((`status` = _utf8mb4'CONSUMED') and (`consumed_at` is not null)) or ((`status` <> _utf8mb4'CONSUMED') and (`consumed_at` is null)))) or ((`operation_type` not in (_utf8mb4'MIGRATION_PREVIEW',_utf8mb4'RECOVERY_PREVIEW')) and (`expires_at` is null) and (`consumed_at` is null) and (`confirmation_token_hash` is null)))),
  CONSTRAINT `ck_plat_tenant_lifecycle_status` CHECK ((`status` in (_utf8mb4'PREVIEWED',_utf8mb4'BLOCKED',_utf8mb4'CONSUMED',_utf8mb4'EXPIRED',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED'))),
  CONSTRAINT `ck_plat_tenant_lifecycle_target` CHECK ((((`operation_type` in (_utf8mb4'MIGRATION_PREVIEW',_utf8mb4'MIGRATION')) and (`target_tenant_id` is not null) and (`target_tenant_id` <> `source_tenant_id`)) or ((`operation_type` in (_utf8mb4'BACKUP',_utf8mb4'RECOVERY_PREVIEW',_utf8mb4'RECOVERY')) and (`target_tenant_id` is null)))),
  CONSTRAINT `ck_plat_tenant_lifecycle_time` CHECK (((`started_at` >= `requested_at`) and (`finished_at` >= `started_at`))),
  CONSTRAINT `ck_plat_tenant_lifecycle_type` CHECK ((`operation_type` in (_utf8mb4'BACKUP',_utf8mb4'MIGRATION_PREVIEW',_utf8mb4'MIGRATION',_utf8mb4'RECOVERY_PREVIEW',_utf8mb4'RECOVERY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_plat_tenant_lifecycle_operation`
--

LOCK TABLES `un_plat_tenant_lifecycle_operation` WRITE;
/*!40000 ALTER TABLE `un_plat_tenant_lifecycle_operation` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_plat_tenant_lifecycle_operation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_audit_event`
--

DROP TABLE IF EXISTS `un_platform_ai_audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_audit_event` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `aggregate_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `aggregate_id` bigint NOT NULL,
  `event_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_platform_ai_audit_account` (`account_id`,`created_at`,`id`),
  KEY `idx_platform_ai_audit_trace` (`trace_id`,`created_at`),
  CONSTRAINT `fk_platform_ai_audit_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_audit_aggregate` CHECK (regexp_like(`aggregate_type`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,31}$')),
  CONSTRAINT `ck_platform_ai_audit_correlation` CHECK ((regexp_like(`request_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`trace_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'))),
  CONSTRAINT `ck_platform_ai_audit_event` CHECK ((regexp_like(`event_type`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_platform_ai_audit_hash` CHECK (regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_ai_audit_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`aggregate_id` > 0))),
  CONSTRAINT `ck_platform_ai_audit_scope` CHECK ((`scope` = _utf8mb4'PLATFORM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_audit_event`
--

LOCK TABLES `un_platform_ai_audit_event` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_audit_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_audit_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_evidence`
--

DROP TABLE IF EXISTS `un_platform_ai_evidence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_evidence` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `evidence_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `projection_json` json NOT NULL,
  `projection_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `returned_systems` smallint unsigned NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_evidence_turn` (`account_id`,`turn_id`),
  CONSTRAINT `fk_platform_ai_evidence_turn` FOREIGN KEY (`account_id`, `turn_id`) REFERENCES `un_platform_ai_turn` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_evidence_hashes` CHECK ((regexp_like(`plan_hash`,_ascii'^[0-9a-f]{64}$') and regexp_like(`projection_hash`,_ascii'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_platform_ai_evidence_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`turn_id` > 0))),
  CONSTRAINT `ck_platform_ai_evidence_json` CHECK (((json_type(`projection_json`) in (_utf8mb4'ARRAY',_utf8mb4'OBJECT')) and (json_length(`projection_json`) <= 100))),
  CONSTRAINT `ck_platform_ai_evidence_result` CHECK (((`returned_systems` <= 100) and regexp_like(`result_code`,_ascii'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_platform_ai_evidence_scope` CHECK ((`scope` = _ascii'PLATFORM')),
  CONSTRAINT `ck_platform_ai_evidence_type` CHECK ((`evidence_type` in (_utf8mb4'AUTHORIZED_SYSTEMS',_utf8mb4'SWITCH_GUIDANCE',_utf8mb4'PERSONAL_TASKS',_utf8mb4'AI_QUOTA',_utf8mb4'SERVICE_HEALTH',_utf8mb4'AGENT_ACTIVITY',_utf8mb4'OPERATIONS_CLARIFICATION')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_evidence`
--

LOCK TABLES `un_platform_ai_evidence` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_evidence` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_evidence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_message`
--

DROP TABLE IF EXISTS `un_platform_ai_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_message` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `role` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `redacted_summary` varchar(200) NOT NULL,
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_length` int unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_platform_ai_message_session` (`account_id`,`session_id`,`created_at`,`id`),
  KEY `fk_platform_ai_message_turn` (`account_id`,`session_id`,`turn_id`),
  CONSTRAINT `fk_platform_ai_message_session` FOREIGN KEY (`account_id`, `session_id`) REFERENCES `un_platform_ai_session` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_message_turn` FOREIGN KEY (`account_id`, `session_id`, `turn_id`) REFERENCES `un_platform_ai_turn` (`account_id`, `session_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_message_hash` CHECK (regexp_like(`content_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_ai_message_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`session_id` > 0) and (`turn_id` > 0))),
  CONSTRAINT `ck_platform_ai_message_role` CHECK ((`role` in (_utf8mb4'USER',_utf8mb4'ASSISTANT'))),
  CONSTRAINT `ck_platform_ai_message_scope` CHECK ((`scope` = _utf8mb4'PLATFORM')),
  CONSTRAINT `ck_platform_ai_message_size` CHECK ((`content_length` <= 128000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_message`
--

LOCK TABLES `un_platform_ai_message` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_policy`
--

DROP TABLE IF EXISTS `un_platform_ai_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_policy` (
  `id` bigint NOT NULL,
  `singleton_key` tinyint GENERATED ALWAYS AS (1) STORED,
  `revision` bigint unsigned NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `draft_json` json NOT NULL,
  `draft_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_policy_singleton` (`singleton_key`),
  KEY `idx_platform_ai_policy_provider` (`provider_id`),
  KEY `fk_platform_ai_policy_updater` (`updated_by`),
  KEY `fk_platform_ai_policy_active_version` (`id`,`active_version_id`),
  CONSTRAINT `fk_platform_ai_policy_active_version` FOREIGN KEY (`id`, `active_version_id`) REFERENCES `un_platform_ai_policy_version` (`policy_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_policy_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_platform_ai_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_policy_updater` FOREIGN KEY (`updated_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_policy_active` CHECK (((`active_version_id` is null) or (`active_version_id` > 0))),
  CONSTRAINT `ck_platform_ai_policy_hash` CHECK (regexp_like(`draft_hash`,_ascii'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_ai_policy_identity` CHECK (((`id` > 0) and (`revision` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`updated_by` > 0))),
  CONSTRAINT `ck_platform_ai_policy_json` CHECK ((json_type(`draft_json`) = _utf8mb4'OBJECT')),
  CONSTRAINT `ck_platform_ai_policy_status` CHECK ((`status` in (_ascii'DRAFT',_ascii'CHECKED',_ascii'PUBLISHED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_policy`
--

LOCK TABLES `un_platform_ai_policy` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_policy` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_policy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_policy_check`
--

DROP TABLE IF EXISTS `un_platform_ai_policy_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_policy_check` (
  `id` bigint NOT NULL,
  `policy_id` bigint NOT NULL,
  `draft_revision` bigint unsigned NOT NULL,
  `draft_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issues_json` json NOT NULL,
  `checked_at` datetime(6) NOT NULL,
  `checked_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_policy_check_revision` (`policy_id`,`draft_revision`),
  KEY `fk_platform_ai_policy_check_actor` (`checked_by`),
  CONSTRAINT `fk_platform_ai_policy_check_actor` FOREIGN KEY (`checked_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_policy_check_root` FOREIGN KEY (`policy_id`) REFERENCES `un_platform_ai_policy` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_policy_check_hash` CHECK (regexp_like(`draft_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_ai_policy_check_identity` CHECK (((`id` > 0) and (`policy_id` > 0) and (`draft_revision` > 0) and (`checked_by` > 0))),
  CONSTRAINT `ck_platform_ai_policy_check_issues` CHECK (((json_type(`issues_json`) = _utf8mb4'ARRAY') and (json_length(`issues_json`) <= 64)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_policy_check`
--

LOCK TABLES `un_platform_ai_policy_check` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_policy_check` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_policy_check` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_policy_publish_replay`
--

DROP TABLE IF EXISTS `un_platform_ai_policy_publish_replay`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_policy_publish_replay` (
  `id` bigint NOT NULL,
  `policy_id` bigint NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_policy_publish_request` (`policy_id`,`request_key`),
  KEY `fk_platform_ai_policy_publish_version` (`policy_id`,`version_id`),
  CONSTRAINT `fk_platform_ai_policy_publish_root` FOREIGN KEY (`policy_id`) REFERENCES `un_platform_ai_policy` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_policy_publish_version` FOREIGN KEY (`policy_id`, `version_id`) REFERENCES `un_platform_ai_policy_version` (`policy_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_policy_publish_hash` CHECK (regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_ai_policy_publish_identity` CHECK (((`id` > 0) and (`policy_id` > 0) and (`version_id` > 0))),
  CONSTRAINT `ck_platform_ai_policy_publish_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_policy_publish_replay`
--

LOCK TABLES `un_platform_ai_policy_publish_replay` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_policy_publish_replay` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_policy_publish_replay` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_policy_version`
--

DROP TABLE IF EXISTS `un_platform_ai_policy_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_policy_version` (
  `id` bigint NOT NULL,
  `policy_id` bigint NOT NULL,
  `version_no` int unsigned NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `model_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `snapshot_json` json NOT NULL,
  `max_systems` smallint unsigned NOT NULL,
  `daily_request_quota` int unsigned NOT NULL,
  `daily_token_quota` bigint unsigned NOT NULL,
  `max_concurrency` tinyint unsigned NOT NULL,
  `strict_redaction` tinyint(1) NOT NULL,
  `data_residency` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `published_at` datetime(6) NOT NULL,
  `published_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_policy_version_no` (`policy_id`,`version_no`),
  UNIQUE KEY `uk_platform_ai_policy_version_root` (`policy_id`,`id`),
  KEY `idx_platform_ai_policy_version_provider` (`provider_id`,`provider_version`),
  KEY `fk_platform_ai_policy_version_publisher` (`published_by`),
  CONSTRAINT `fk_platform_ai_policy_version_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_platform_ai_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_policy_version_publisher` FOREIGN KEY (`published_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_policy_version_root` FOREIGN KEY (`policy_id`) REFERENCES `un_platform_ai_policy` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_policy_version_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_platform_ai_policy_version_hash` CHECK (regexp_like(`snapshot_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_ai_policy_version_identity` CHECK (((`id` > 0) and (`policy_id` > 0) and (`version_no` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`published_by` > 0))),
  CONSTRAINT `ck_platform_ai_policy_version_limits` CHECK (((`max_systems` between 1 and 100) and (`daily_request_quota` between 1 and 10000) and (`daily_token_quota` between 10000 and 10000000) and (`max_concurrency` between 1 and 16))),
  CONSTRAINT `ck_platform_ai_policy_version_prompt` CHECK (regexp_like(`prompt_version`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$')),
  CONSTRAINT `ck_platform_ai_policy_version_redaction` CHECK (((`strict_redaction` = 1) and (`data_residency` = _utf8mb4'PLATFORM_METADATA_ONLY'))),
  CONSTRAINT `ck_platform_ai_policy_version_snapshot` CHECK ((json_type(`snapshot_json`) = _utf8mb4'OBJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_policy_version`
--

LOCK TABLES `un_platform_ai_policy_version` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_policy_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_policy_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_provider`
--

DROP TABLE IF EXISTS `un_platform_ai_provider`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_provider` (
  `id` bigint NOT NULL,
  `code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(160) NOT NULL,
  `base_url` varchar(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `timeout_seconds` tinyint unsigned NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_provider_code` (`code`),
  KEY `idx_platform_ai_provider_list` (`updated_at` DESC,`id` DESC),
  KEY `fk_platform_ai_provider_creator` (`created_by`),
  KEY `fk_platform_ai_provider_updater` (`updated_by`),
  CONSTRAINT `fk_platform_ai_provider_creator` FOREIGN KEY (`created_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_provider_updater` FOREIGN KEY (`updated_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_provider_code` CHECK (regexp_like(`code`,_utf8mb4'^[A-Za-z][A-Za-z0-9_]{0,63}$')),
  CONSTRAINT `ck_platform_ai_provider_identity` CHECK (((`id` > 0) and (`created_by` > 0) and (`updated_by` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_platform_ai_provider_limits` CHECK (((`timeout_seconds` between 1 and 30) and (`enabled` in (0,1)))),
  CONSTRAINT `ck_platform_ai_provider_model` CHECK ((char_length(trim(`model_code`)) between 1 and 128)),
  CONSTRAINT `ck_platform_ai_provider_name` CHECK ((char_length(trim(`name`)) between 1 and 160)),
  CONSTRAINT `ck_platform_ai_provider_secret_ref` CHECK (((char_length(`secret_ref`) between 3 and 512) and (`secret_ref` = trim(`secret_ref`)) and regexp_like(`secret_ref`,_utf8mb4'^[A-Za-z][A-Za-z0-9+.-]{1,31}://[^[:space:]]+$'))),
  CONSTRAINT `ck_platform_ai_provider_times` CHECK ((`updated_at` >= `created_at`)),
  CONSTRAINT `ck_platform_ai_provider_url` CHECK (((char_length(`base_url`) between 8 and 1024) and (`base_url` = trim(`base_url`)) and (not((`base_url` like _utf8mb4'%@%'))) and (not((`base_url` like _utf8mb4'%?%'))) and (not((`base_url` like _utf8mb4'%#%')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_provider`
--

LOCK TABLES `un_platform_ai_provider` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_provider` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_provider` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_quota_bucket`
--

DROP TABLE IF EXISTS `un_platform_ai_quota_bucket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_quota_bucket` (
  `account_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `period_start` datetime(6) NOT NULL,
  `request_count` int unsigned NOT NULL,
  `used_tokens` bigint unsigned NOT NULL,
  `reserved_tokens` bigint unsigned NOT NULL,
  `running_count` int unsigned NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`account_id`,`policy_version_id`,`period_start`),
  KEY `fk_platform_ai_quota_policy` (`policy_version_id`),
  CONSTRAINT `fk_platform_ai_quota_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_quota_policy` FOREIGN KEY (`policy_version_id`) REFERENCES `un_platform_ai_policy_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_quota_counts` CHECK (((`request_count` <= 10000) and (`used_tokens` <= 10000000) and (`reserved_tokens` <= 10000000) and (`running_count` <= 16))),
  CONSTRAINT `ck_platform_ai_quota_identity` CHECK (((`account_id` > 0) and (`policy_version_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_quota_bucket`
--

LOCK TABLES `un_platform_ai_quota_bucket` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_quota_bucket` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_quota_bucket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_session`
--

DROP TABLE IF EXISTS `un_platform_ai_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_session` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `title_summary` varchar(200) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_session_account` (`account_id`,`id`),
  KEY `idx_platform_ai_session_list` (`account_id`,`updated_at` DESC,`id` DESC),
  CONSTRAINT `fk_platform_ai_session_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_session_identity` CHECK (((`id` > 0) and (`account_id` > 0))),
  CONSTRAINT `ck_platform_ai_session_scope` CHECK ((`scope` = _utf8mb4'PLATFORM')),
  CONSTRAINT `ck_platform_ai_session_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CLOSED'))),
  CONSTRAINT `ck_platform_ai_session_times` CHECK ((`updated_at` >= `created_at`)),
  CONSTRAINT `ck_platform_ai_session_title` CHECK ((char_length(trim(`title_summary`)) between 1 and 200))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_session`
--

LOCK TABLES `un_platform_ai_session` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_task_proposal`
--

DROP TABLE IF EXISTS `un_platform_ai_task_proposal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_task_proposal` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `title_summary` varchar(200) DEFAULT NULL,
  `description_summary` varchar(200) DEFAULT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `priority` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `confidence` decimal(5,4) NOT NULL,
  `clarification_summary` varchar(200) DEFAULT NULL,
  `sealed_ciphertext` mediumtext CHARACTER SET ascii COLLATE ascii_bin,
  `sealed_key_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `sealed_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `task_title_summary` varchar(200) DEFAULT NULL,
  `task_description_summary` varchar(200) DEFAULT NULL,
  `task_due_at` datetime(6) DEFAULT NULL,
  `task_priority` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `task_status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `task_source` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `task_created_at` datetime(6) DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `owner_trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_task_proposal_account` (`account_id`,`id`),
  UNIQUE KEY `uk_platform_ai_task_proposal_turn` (`account_id`,`turn_id`),
  KEY `idx_platform_ai_task_proposal_session` (`account_id`,`session_id`,`created_at`,`id`),
  KEY `idx_platform_ai_task_proposal_state` (`account_id`,`state`,`expires_at`,`id`),
  KEY `idx_platform_ai_task_proposal_policy` (`policy_version_id`),
  KEY `idx_platform_ai_task_proposal_provider` (`provider_id`),
  KEY `idx_platform_ai_task_proposal_task` (`task_id`),
  KEY `fk_platform_ai_task_proposal_turn` (`account_id`,`session_id`,`turn_id`),
  KEY `fk_platform_ai_task_proposal_confirmer` (`confirmed_by`),
  CONSTRAINT `fk_platform_ai_task_proposal_confirmer` FOREIGN KEY (`confirmed_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_proposal_policy` FOREIGN KEY (`policy_version_id`) REFERENCES `un_platform_ai_policy_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_proposal_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_platform_ai_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_proposal_result` FOREIGN KEY (`task_id`) REFERENCES `un_platform_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_proposal_session` FOREIGN KEY (`account_id`, `session_id`) REFERENCES `un_platform_ai_session` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_proposal_turn` FOREIGN KEY (`account_id`, `session_id`, `turn_id`) REFERENCES `un_platform_ai_turn` (`account_id`, `session_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_task_proposal_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0) and (`revision` >= 0))),
  CONSTRAINT `ck_platform_ai_task_proposal_owner_correlation` CHECK ((((`owner_request_id` is null) and (`owner_trace_id` is null)) or (regexp_like(`owner_request_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`owner_trace_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')))),
  CONSTRAINT `ck_platform_ai_task_proposal_plan` CHECK (regexp_like(`plan_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_ai_task_proposal_preview` CHECK (((`confidence` between 0.0000 and 1.0000) and (((`state` = _utf8mb4'CLARIFICATION_REQUIRED') and (`title_summary` is null) and (`description_summary` is null) and (`due_at` is null) and (`priority` is null) and (char_length(trim(`clarification_summary`)) between 1 and 200)) or ((`state` <> _utf8mb4'CLARIFICATION_REQUIRED') and (char_length(trim(`title_summary`)) between 1 and 200) and ((`description_summary` is null) or (char_length(trim(`description_summary`)) between 1 and 200)) and (`priority` in (_utf8mb4'LOW',_utf8mb4'NORMAL',_utf8mb4'HIGH',_utf8mb4'URGENT')) and (`clarification_summary` is null))))),
  CONSTRAINT `ck_platform_ai_task_proposal_prompt` CHECK (regexp_like(`prompt_version`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$')),
  CONSTRAINT `ck_platform_ai_task_proposal_result` CHECK ((((`state` = _utf8mb4'SUCCEEDED') and (`confirmed_by` = `account_id`) and (`task_id` > 0) and (char_length(trim(`task_title_summary`)) between 1 and 200) and (`task_priority` in (_utf8mb4'LOW',_utf8mb4'NORMAL',_utf8mb4'HIGH',_utf8mb4'URGENT')) and (`task_status` = _utf8mb4'OPEN') and (`task_source` = _utf8mb4'AGENT') and (`task_created_at` is not null)) or ((`state` <> _utf8mb4'SUCCEEDED') and (`task_id` is null) and (`task_title_summary` is null) and (`task_description_summary` is null) and (`task_due_at` is null) and (`task_priority` is null) and (`task_status` is null) and (`task_source` is null) and (`task_created_at` is null)))),
  CONSTRAINT `ck_platform_ai_task_proposal_result_code` CHECK (regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_platform_ai_task_proposal_scope` CHECK ((`scope` = _utf8mb4'PLATFORM')),
  CONSTRAINT `ck_platform_ai_task_proposal_sealed` CHECK ((((`state` = _utf8mb4'CLARIFICATION_REQUIRED') and (`sealed_ciphertext` is null) and (`sealed_key_version` is null) and (`sealed_hash` is null)) or ((`state` <> _utf8mb4'CLARIFICATION_REQUIRED') and (char_length(`sealed_ciphertext`) between 1 and 131072) and (char_length(`sealed_key_version`) between 1 and 64) and regexp_like(`sealed_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_platform_ai_task_proposal_state` CHECK ((`state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `ck_platform_ai_task_proposal_times` CHECK (((`expires_at` > `created_at`) and (`updated_at` >= `created_at`) and ((`finished_at` is null) or (`finished_at` >= `created_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_task_proposal`
--

LOCK TABLES `un_platform_ai_task_proposal` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_task_proposal` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_task_proposal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_task_proposal_attempt`
--

DROP TABLE IF EXISTS `un_platform_ai_task_proposal_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_task_proposal_attempt` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_task_attempt_account` (`account_id`,`id`),
  UNIQUE KEY `uk_platform_ai_task_attempt_request` (`account_id`,`proposal_id`,`action`,`request_key`),
  KEY `idx_platform_ai_task_attempt_proposal` (`account_id`,`proposal_id`,`created_at`,`id`),
  KEY `fk_platform_ai_task_attempt_turn` (`account_id`,`session_id`,`turn_id`),
  KEY `fk_platform_ai_task_attempt_policy` (`policy_version_id`),
  KEY `fk_platform_ai_task_attempt_provider` (`provider_id`),
  CONSTRAINT `fk_platform_ai_task_attempt_policy` FOREIGN KEY (`policy_version_id`) REFERENCES `un_platform_ai_policy_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_attempt_proposal` FOREIGN KEY (`account_id`, `proposal_id`) REFERENCES `un_platform_ai_task_proposal` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_attempt_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_platform_ai_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_attempt_session` FOREIGN KEY (`account_id`, `session_id`) REFERENCES `un_platform_ai_session` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_attempt_turn` FOREIGN KEY (`account_id`, `session_id`, `turn_id`) REFERENCES `un_platform_ai_turn` (`account_id`, `session_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_task_attempt_action` CHECK ((`action` in (_utf8mb4'CONFIRM',_utf8mb4'REJECT'))),
  CONSTRAINT `ck_platform_ai_task_attempt_hash` CHECK ((regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`result_hash` is null) or regexp_like(`result_hash`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_platform_ai_task_attempt_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0))),
  CONSTRAINT `ck_platform_ai_task_attempt_key` CHECK (regexp_like(`request_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
  CONSTRAINT `ck_platform_ai_task_attempt_result` CHECK (regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_platform_ai_task_attempt_scope` CHECK ((`scope` = _utf8mb4'PLATFORM')),
  CONSTRAINT `ck_platform_ai_task_attempt_state` CHECK ((((`status` = _utf8mb4'EXECUTING') and (`finished_at` is null) and (`result_hash` is null)) or ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')) and (`finished_at` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_task_proposal_attempt`
--

LOCK TABLES `un_platform_ai_task_proposal_attempt` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_task_proposal_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_task_proposal_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_task_proposal_event`
--

DROP TABLE IF EXISTS `un_platform_ai_task_proposal_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_task_proposal_event` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `proposal_id` bigint NOT NULL,
  `attempt_id` bigint DEFAULT NULL,
  `event_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `from_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `to_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `actor_account_id` bigint NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_platform_ai_task_event_proposal` (`account_id`,`proposal_id`,`revision`,`id`),
  KEY `idx_platform_ai_task_event_attempt` (`account_id`,`attempt_id`),
  KEY `fk_platform_ai_task_event_turn` (`account_id`,`session_id`,`turn_id`),
  KEY `fk_platform_ai_task_event_policy` (`policy_version_id`),
  KEY `fk_platform_ai_task_event_provider` (`provider_id`),
  KEY `fk_platform_ai_task_event_actor` (`actor_account_id`),
  CONSTRAINT `fk_platform_ai_task_event_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_event_attempt` FOREIGN KEY (`account_id`, `attempt_id`) REFERENCES `un_platform_ai_task_proposal_attempt` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_event_policy` FOREIGN KEY (`policy_version_id`) REFERENCES `un_platform_ai_policy_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_event_proposal` FOREIGN KEY (`account_id`, `proposal_id`) REFERENCES `un_platform_ai_task_proposal` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_event_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_platform_ai_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_event_session` FOREIGN KEY (`account_id`, `session_id`) REFERENCES `un_platform_ai_session` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_task_event_turn` FOREIGN KEY (`account_id`, `session_id`, `turn_id`) REFERENCES `un_platform_ai_turn` (`account_id`, `session_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_task_event_correlation` CHECK ((regexp_like(`request_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`trace_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'))),
  CONSTRAINT `ck_platform_ai_task_event_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`session_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`proposal_id` > 0) and ((`attempt_id` is null) or (`attempt_id` > 0)) and (`actor_account_id` = `account_id`) and (`revision` >= 0))),
  CONSTRAINT `ck_platform_ai_task_event_result` CHECK ((regexp_like(`result_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and regexp_like(`event_hash`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `ck_platform_ai_task_event_scope` CHECK ((`scope` = _utf8mb4'PLATFORM')),
  CONSTRAINT `ck_platform_ai_task_event_state` CHECK (((`to_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')) and ((`from_state` is null) or (`from_state` in (_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'PENDING',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))))),
  CONSTRAINT `ck_platform_ai_task_event_type` CHECK ((`event_type` in (_utf8mb4'PROPOSED',_utf8mb4'CLARIFICATION_REQUIRED',_utf8mb4'CONFIRMING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_task_proposal_event`
--

LOCK TABLES `un_platform_ai_task_proposal_event` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_task_proposal_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_task_proposal_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_turn`
--

DROP TABLE IF EXISTS `un_platform_ai_turn`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_turn` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `operation` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_summary` varchar(200) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `response_summary` varchar(200) DEFAULT NULL,
  `response_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `returned_systems` smallint unsigned NOT NULL,
  `result_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `retryable` tinyint(1) NOT NULL,
  `reserved_tokens` int unsigned NOT NULL,
  `latency_ms` bigint unsigned NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_turn_account` (`account_id`,`id`),
  UNIQUE KEY `uk_platform_ai_turn_session_identity` (`account_id`,`session_id`,`id`),
  KEY `idx_platform_ai_turn_session` (`account_id`,`session_id`,`created_at`,`id`),
  KEY `idx_platform_ai_turn_trace` (`trace_id`,`created_at`),
  KEY `fk_platform_ai_turn_policy` (`policy_version_id`),
  KEY `fk_platform_ai_turn_provider` (`provider_id`),
  CONSTRAINT `fk_platform_ai_turn_policy` FOREIGN KEY (`policy_version_id`) REFERENCES `un_platform_ai_policy_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_turn_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_platform_ai_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_turn_session` FOREIGN KEY (`account_id`, `session_id`) REFERENCES `un_platform_ai_session` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_turn_correlation` CHECK ((regexp_like(`request_id`,_ascii'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`trace_id`,_ascii'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'))),
  CONSTRAINT `ck_platform_ai_turn_hashes` CHECK ((regexp_like(`request_hash`,_ascii'^[0-9a-f]{64}$') and ((`plan_hash` is null) or regexp_like(`plan_hash`,_ascii'^[0-9a-f]{64}$')) and ((`response_hash` is null) or regexp_like(`response_hash`,_ascii'^[0-9a-f]{64}$')))),
  CONSTRAINT `ck_platform_ai_turn_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`session_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0) and (`authorization_epoch` > 0))),
  CONSTRAINT `ck_platform_ai_turn_operation` CHECK ((`operation` in (_utf8mb4'UNRESOLVED',_utf8mb4'AUTHORIZED_SYSTEMS_QUERY',_utf8mb4'SYSTEM_SWITCH_GUIDANCE',_utf8mb4'PLATFORM_TASK_DRAFT',_utf8mb4'PLATFORM_OPERATIONS_QUERY'))),
  CONSTRAINT `ck_platform_ai_turn_result` CHECK (((`returned_systems` <= 100) and (`retryable` in (0,1)) and regexp_like(`result_code`,_ascii'^[A-Z][A-Z0-9_]{1,63}$'))),
  CONSTRAINT `ck_platform_ai_turn_scope` CHECK ((`scope` = _ascii'PLATFORM')),
  CONSTRAINT `ck_platform_ai_turn_state` CHECK ((((`status` = _ascii'RUNNING') and (`finished_at` is null) and (`retryable` = 0)) or ((`status` = _ascii'RETRYABLE') and (`finished_at` is not null) and (`retryable` = 1)) or ((`status` in (_ascii'SUCCEEDED',_ascii'FAILED')) and (`finished_at` is not null) and (`retryable` = 0)))),
  CONSTRAINT `ck_platform_ai_turn_status` CHECK ((`status` in (_ascii'RUNNING',_ascii'SUCCEEDED',_ascii'FAILED',_ascii'RETRYABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_turn`
--

LOCK TABLES `un_platform_ai_turn` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_turn` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_turn` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_ai_usage`
--

DROP TABLE IF EXISTS `un_platform_ai_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_ai_usage` (
  `id` bigint NOT NULL,
  `scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `turn_id` bigint NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `call_count` tinyint unsigned NOT NULL,
  `prompt_tokens` int unsigned NOT NULL,
  `completion_tokens` int unsigned NOT NULL,
  `total_tokens` int unsigned NOT NULL,
  `latency_ms` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_usage_turn` (`account_id`,`turn_id`),
  KEY `fk_platform_ai_usage_policy` (`policy_version_id`),
  KEY `fk_platform_ai_usage_provider` (`provider_id`),
  CONSTRAINT `fk_platform_ai_usage_policy` FOREIGN KEY (`policy_version_id`) REFERENCES `un_platform_ai_policy_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_usage_provider` FOREIGN KEY (`provider_id`) REFERENCES `un_platform_ai_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_ai_usage_turn` FOREIGN KEY (`account_id`, `turn_id`) REFERENCES `un_platform_ai_turn` (`account_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_ai_usage_counts` CHECK (((`call_count` between 0 and 2) and (`total_tokens` = (`prompt_tokens` + `completion_tokens`)))),
  CONSTRAINT `ck_platform_ai_usage_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`turn_id` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0))),
  CONSTRAINT `ck_platform_ai_usage_scope` CHECK ((`scope` = _utf8mb4'PLATFORM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_ai_usage`
--

LOCK TABLES `un_platform_ai_usage` WRITE;
/*!40000 ALTER TABLE `un_platform_ai_usage` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_ai_usage` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_dashboard`
--

DROP TABLE IF EXISTS `un_platform_dashboard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_dashboard` (
  `id` bigint NOT NULL,
  `code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(200) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `draft_json` json NOT NULL,
  `draft_version` bigint unsigned NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `active_version_number` int unsigned DEFAULT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_dashboard_code` (`code`),
  KEY `idx_platform_dashboard_runtime` (`status`,`updated_at` DESC,`id` DESC),
  KEY `fk_platform_dashboard_creator` (`created_by`),
  KEY `fk_platform_dashboard_updater` (`updated_by`),
  KEY `fk_platform_dashboard_active` (`active_version_id`),
  CONSTRAINT `fk_platform_dashboard_active` FOREIGN KEY (`active_version_id`) REFERENCES `un_platform_dashboard_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_dashboard_creator` FOREIGN KEY (`created_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_platform_dashboard_updater` FOREIGN KEY (`updated_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `ck_platform_dashboard_active` CHECK ((((`active_version_id` is null) and (`active_version_number` is null) and (`status` = _ascii'DRAFT')) or ((`active_version_id` is not null) and (`active_version_number` > 0) and (`status` in (_ascii'PUBLISHED',_ascii'DISABLED'))))),
  CONSTRAINT `ck_platform_dashboard_code` CHECK (regexp_like(`code`,_ascii'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_platform_dashboard_status` CHECK ((`status` in (_ascii'DRAFT',_ascii'PUBLISHED',_ascii'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_dashboard`
--

LOCK TABLES `un_platform_dashboard` WRITE;
/*!40000 ALTER TABLE `un_platform_dashboard` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_dashboard` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_dashboard_version`
--

DROP TABLE IF EXISTS `un_platform_dashboard_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_dashboard_version` (
  `id` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  `version_number` int unsigned NOT NULL,
  `snapshot_json` json NOT NULL,
  `checksum` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `published_at` datetime(6) NOT NULL,
  `published_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_dashboard_version_number` (`dashboard_id`,`version_number`),
  KEY `fk_platform_dashboard_version_publisher` (`published_by`),
  CONSTRAINT `fk_platform_dashboard_version_publisher` FOREIGN KEY (`published_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_platform_dashboard_version_root` FOREIGN KEY (`dashboard_id`) REFERENCES `un_platform_dashboard` (`id`),
  CONSTRAINT `ck_platform_dashboard_version_checksum` CHECK (regexp_like(`checksum`,_utf8mb4'^[0-9a-f]{64}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_dashboard_version`
--

LOCK TABLES `un_platform_dashboard_version` WRITE;
/*!40000 ALTER TABLE `un_platform_dashboard_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_dashboard_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_flow_definition`
--

DROP TABLE IF EXISTS `un_platform_flow_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_flow_definition` (
  `id` bigint NOT NULL,
  `code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(200) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `draft_json` json NOT NULL,
  `draft_version` bigint unsigned NOT NULL,
  `active_version_id` bigint DEFAULT NULL,
  `active_version_number` int unsigned DEFAULT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_flow_definition_code` (`code`),
  KEY `idx_platform_flow_definition_status` (`status`,`updated_at` DESC,`id` DESC),
  KEY `fk_platform_flow_definition_creator` (`created_by`),
  KEY `fk_platform_flow_definition_updater` (`updated_by`),
  KEY `fk_platform_flow_definition_active` (`active_version_id`),
  CONSTRAINT `fk_platform_flow_definition_active` FOREIGN KEY (`active_version_id`) REFERENCES `un_platform_flow_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_flow_definition_creator` FOREIGN KEY (`created_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_platform_flow_definition_updater` FOREIGN KEY (`updated_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `ck_platform_flow_definition_active` CHECK ((((`active_version_id` is null) and (`active_version_number` is null) and (`status` = _ascii'DRAFT')) or ((`active_version_id` is not null) and (`active_version_number` > 0) and (`status` in (_ascii'PUBLISHED',_ascii'DISABLED'))))),
  CONSTRAINT `ck_platform_flow_definition_code` CHECK (regexp_like(`code`,_ascii'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `ck_platform_flow_definition_status` CHECK ((`status` in (_ascii'DRAFT',_ascii'PUBLISHED',_ascii'DISABLED'))),
  CONSTRAINT `ck_platform_flow_definition_version` CHECK (((`draft_version` > 0) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_flow_definition`
--

LOCK TABLES `un_platform_flow_definition` WRITE;
/*!40000 ALTER TABLE `un_platform_flow_definition` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_flow_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_flow_instance`
--

DROP TABLE IF EXISTS `un_platform_flow_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_flow_instance` (
  `id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `definition_version_id` bigint NOT NULL,
  `starter_account_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_json` json NOT NULL,
  `result_json` json DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_platform_flow_instance_starter` (`starter_account_id`,`started_at` DESC,`id` DESC),
  KEY `idx_platform_flow_instance_definition` (`definition_id`,`started_at` DESC,`id` DESC),
  KEY `fk_platform_flow_instance_version` (`definition_version_id`),
  CONSTRAINT `fk_platform_flow_instance_definition` FOREIGN KEY (`definition_id`) REFERENCES `un_platform_flow_definition` (`id`),
  CONSTRAINT `fk_platform_flow_instance_starter` FOREIGN KEY (`starter_account_id`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `fk_platform_flow_instance_version` FOREIGN KEY (`definition_version_id`) REFERENCES `un_platform_flow_version` (`id`),
  CONSTRAINT `ck_platform_flow_instance_result` CHECK ((((`status` = _utf8mb4'RUNNING') and (`result_json` is null) and (`completed_at` is null)) or ((`status` in (_utf8mb4'COMPLETED',_utf8mb4'FAILED')) and (`result_json` is not null) and (`completed_at` >= `started_at`)))),
  CONSTRAINT `ck_platform_flow_instance_status` CHECK ((`status` in (_utf8mb4'RUNNING',_utf8mb4'COMPLETED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_flow_instance`
--

LOCK TABLES `un_platform_flow_instance` WRITE;
/*!40000 ALTER TABLE `un_platform_flow_instance` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_flow_instance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_flow_version`
--

DROP TABLE IF EXISTS `un_platform_flow_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_flow_version` (
  `id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `version_number` int unsigned NOT NULL,
  `snapshot_json` json NOT NULL,
  `checksum` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `published_at` datetime(6) NOT NULL,
  `published_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_flow_version_number` (`definition_id`,`version_number`),
  KEY `fk_platform_flow_version_publisher` (`published_by`),
  CONSTRAINT `fk_platform_flow_version_definition` FOREIGN KEY (`definition_id`) REFERENCES `un_platform_flow_definition` (`id`),
  CONSTRAINT `fk_platform_flow_version_publisher` FOREIGN KEY (`published_by`) REFERENCES `un_plat_account` (`id`),
  CONSTRAINT `ck_platform_flow_version_checksum` CHECK (regexp_like(`checksum`,_utf8mb4'^[0-9a-f]{64}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_flow_version`
--

LOCK TABLES `un_platform_flow_version` WRITE;
/*!40000 ALTER TABLE `un_platform_flow_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_flow_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_inbox_message`
--

DROP TABLE IF EXISTS `un_platform_inbox_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_inbox_message` (
  `id` bigint NOT NULL,
  `recipient_account_id` bigint NOT NULL,
  `template_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `message_type` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `title` varchar(200) NOT NULL,
  `body` varchar(4000) NOT NULL,
  `target_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `target_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `target_path` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `archived_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_platform_message_recipient_list` (`recipient_account_id`,`archived_at`,`read_at`,`created_at` DESC,`id` DESC),
  KEY `idx_platform_message_template` (`recipient_account_id`,`template_code`,`created_at` DESC),
  CONSTRAINT `fk_platform_message_recipient` FOREIGN KEY (`recipient_account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_message_identity` CHECK (((`id` > 0) and (`recipient_account_id` > 0))),
  CONSTRAINT `ck_platform_message_lifecycle` CHECK (((`archived_at` is null) or (`read_at` is not null))),
  CONSTRAINT `ck_platform_message_target` CHECK ((((`target_type` is null) and (`target_id` is null) and (`target_path` is null)) or ((`target_type` in (_utf8mb4'PLATFORM_AUTHORIZATION',_utf8mb4'PLATFORM_TASK',_utf8mb4'PLATFORM_PROJECT',_utf8mb4'PLATFORM_LOG',_utf8mb4'SYSTEM_SWITCH',_utf8mb4'PLATFORM_AGENT')) and (`target_id` is not null) and (`target_path` like _utf8mb4'/platform/%') and (not((`target_path` like _utf8mb4'/systems/%')))))),
  CONSTRAINT `ck_platform_message_text` CHECK (((char_length(trim(`title`)) between 1 and 200) and (char_length(trim(`body`)) between 1 and 4000))),
  CONSTRAINT `ck_platform_message_type` CHECK ((`message_type` in (_utf8mb4'AUTHORIZATION',_utf8mb4'TASK',_utf8mb4'LOG',_utf8mb4'SYSTEM_SWITCH',_utf8mb4'AGENT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_inbox_message`
--

LOCK TABLES `un_platform_inbox_message` WRITE;
/*!40000 ALTER TABLE `un_platform_inbox_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_inbox_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_openapi_application`
--

DROP TABLE IF EXISTS `un_platform_openapi_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_openapi_application` (
  `id` bigint NOT NULL,
  `service_account_id` bigint NOT NULL,
  `app_key` varchar(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(160) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scopes_json` json NOT NULL,
  `ip_allowlist_json` json NOT NULL,
  `rate_limit_per_minute` int unsigned NOT NULL,
  `current_credential_version` int unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_openapi_application_app_key` (`app_key`),
  KEY `idx_platform_openapi_application_account` (`service_account_id`,`status`,`updated_at`,`id`),
  CONSTRAINT `fk_platform_openapi_application_account` FOREIGN KEY (`service_account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_openapi_application_credential` CHECK ((`current_credential_version` between 1 and 2147483647)),
  CONSTRAINT `ck_platform_openapi_application_ids` CHECK (((`id` > 0) and (`service_account_id` > 0) and (`created_by` > 0) and (`updated_by` > 0))),
  CONSTRAINT `ck_platform_openapi_application_ips` CHECK (((json_type(`ip_allowlist_json`) = _utf8mb4'ARRAY') and (json_length(`ip_allowlist_json`) <= 128))),
  CONSTRAINT `ck_platform_openapi_application_key` CHECK (((char_length(`app_key`) between 16 and 96) and regexp_like(`app_key`,_utf8mb4'^[A-Za-z0-9_-]+$'))),
  CONSTRAINT `ck_platform_openapi_application_name` CHECK ((char_length(trim(`name`)) between 1 and 160)),
  CONSTRAINT `ck_platform_openapi_application_rate` CHECK ((`rate_limit_per_minute` between 1 and 60000)),
  CONSTRAINT `ck_platform_openapi_application_scopes` CHECK (((json_type(`scopes_json`) = _utf8mb4'ARRAY') and (json_length(`scopes_json`) between 1 and 64))),
  CONSTRAINT `ck_platform_openapi_application_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_openapi_application`
--

LOCK TABLES `un_platform_openapi_application` WRITE;
/*!40000 ALTER TABLE `un_platform_openapi_application` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_openapi_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_openapi_call_log`
--

DROP TABLE IF EXISTS `un_platform_openapi_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_openapi_call_log` (
  `id` bigint NOT NULL,
  `application_id` bigint DEFAULT NULL,
  `app_key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `credential_version` int unsigned DEFAULT NULL,
  `route_template` varchar(255) NOT NULL,
  `request_method` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_category` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `http_status` smallint unsigned NOT NULL,
  `latency_ms` bigint unsigned NOT NULL,
  `request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `observed_ip` varbinary(16) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_platform_openapi_call_application` (`application_id`,`created_at`,`id`),
  KEY `idx_platform_openapi_call_key` (`app_key_hash`,`created_at`,`id`),
  KEY `idx_platform_openapi_call_result` (`result_category`,`created_at`,`id`),
  KEY `idx_platform_openapi_call_request` (`request_id`),
  KEY `idx_platform_openapi_call_trace` (`trace_id`,`created_at`),
  CONSTRAINT `fk_platform_openapi_call_application` FOREIGN KEY (`application_id`) REFERENCES `un_platform_openapi_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_openapi_call` CHECK (((`id` > 0) and (char_length(`app_key_hash`) = 64) and regexp_like(`app_key_hash`,_utf8mb4'^[0-9a-f]{64}$') and (`request_method` in (_utf8mb4'GET',_utf8mb4'HEAD',_utf8mb4'POST',_utf8mb4'PUT',_utf8mb4'PATCH',_utf8mb4'DELETE',_utf8mb4'OPTIONS')) and (`http_status` between 100 and 599) and (`latency_ms` <= 86400000) and (length(`observed_ip`) in (4,16))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_openapi_call_log`
--

LOCK TABLES `un_platform_openapi_call_log` WRITE;
/*!40000 ALTER TABLE `un_platform_openapi_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_openapi_call_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_openapi_credential`
--

DROP TABLE IF EXISTS `un_platform_openapi_credential`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_openapi_credential` (
  `id` bigint NOT NULL,
  `application_id` bigint NOT NULL,
  `credential_version` int unsigned NOT NULL,
  `secret_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `activated_at` datetime(3) NOT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_openapi_credential_version` (`application_id`,`credential_version`),
  CONSTRAINT `fk_platform_openapi_credential_application` FOREIGN KEY (`application_id`) REFERENCES `un_platform_openapi_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_openapi_credential_lifecycle` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`revoked_at` is null)) or ((`status` = _utf8mb4'REVOKED') and (`revoked_at` is not null) and (`revoked_at` >= `activated_at`)))),
  CONSTRAINT `ck_platform_openapi_credential_ref` CHECK (((char_length(`secret_ref`) between 3 and 512) and (`secret_ref` = trim(`secret_ref`)) and regexp_like(`secret_ref`,_utf8mb4'^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$'))),
  CONSTRAINT `ck_platform_openapi_credential_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_openapi_credential`
--

LOCK TABLES `un_platform_openapi_credential` WRITE;
/*!40000 ALTER TABLE `un_platform_openapi_credential` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_openapi_credential` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_openapi_nonce`
--

DROP TABLE IF EXISTS `un_platform_openapi_nonce`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_openapi_nonce` (
  `application_id` bigint NOT NULL,
  `credential_version` int unsigned NOT NULL,
  `nonce` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`application_id`,`credential_version`,`nonce`),
  KEY `idx_platform_openapi_nonce_expiry` (`expires_at`,`application_id`),
  CONSTRAINT `fk_platform_openapi_nonce_credential` FOREIGN KEY (`application_id`, `credential_version`) REFERENCES `un_platform_openapi_credential` (`application_id`, `credential_version`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_openapi_nonce` CHECK (((char_length(`nonce`) between 16 and 128) and regexp_like(`nonce`,_utf8mb4'^[A-Za-z0-9._~-]+$') and (`expires_at` > `created_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_openapi_nonce`
--

LOCK TABLES `un_platform_openapi_nonce` WRITE;
/*!40000 ALTER TABLE `un_platform_openapi_nonce` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_openapi_nonce` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_openapi_rate_bucket`
--

DROP TABLE IF EXISTS `un_platform_openapi_rate_bucket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_openapi_rate_bucket` (
  `application_id` bigint NOT NULL,
  `window_start` datetime(3) NOT NULL,
  `request_count` int unsigned NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`application_id`,`window_start`),
  KEY `idx_platform_openapi_rate_window` (`window_start`,`application_id`),
  CONSTRAINT `fk_platform_openapi_rate_application` FOREIGN KEY (`application_id`) REFERENCES `un_platform_openapi_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_openapi_rate_window` CHECK (((second(`window_start`) = 0) and (microsecond(`window_start`) = 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_openapi_rate_bucket`
--

LOCK TABLES `un_platform_openapi_rate_bucket` WRITE;
/*!40000 ALTER TABLE `un_platform_openapi_rate_bucket` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_openapi_rate_bucket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_task`
--

DROP TABLE IF EXISTS `un_platform_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_task` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `priority` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_kind` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PERSONAL',
  `project_id` bigint DEFAULT NULL,
  `labels_json` json DEFAULT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `completed_at` datetime(6) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_task_account_request` (`account_id`,`idempotency_key`),
  KEY `idx_platform_task_account_list` (`account_id`,`created_at` DESC,`id` DESC),
  KEY `idx_platform_task_trace` (`trace_id`,`created_at`,`id`),
  KEY `fk_platform_task_creator` (`created_by`),
  KEY `fk_platform_task_project` (`project_id`),
  KEY `idx_platform_task_work_list` (`account_id`,`task_kind`,`status`,`due_at`,`updated_at` DESC),
  CONSTRAINT `fk_platform_task_account` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_task_creator` FOREIGN KEY (`created_by`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_task_project` FOREIGN KEY (`project_id`) REFERENCES `un_platform_work_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_task_correlation` CHECK ((regexp_like(`idempotency_key`,_ascii'^[A-Za-z0-9][A-Za-z0-9_.:/+=-]{0,127}$') and regexp_like(`request_id`,_ascii'^[A-Za-z0-9][A-Za-z0-9_.:/+=-]{0,127}$') and regexp_like(`trace_id`,_ascii'^[A-Za-z0-9][A-Za-z0-9_.:/+=-]{0,127}$'))),
  CONSTRAINT `ck_platform_task_description` CHECK (((`description` is null) or (char_length(trim(`description`)) between 1 and 2000))),
  CONSTRAINT `ck_platform_task_due` CHECK (((`due_at` is null) or (`due_at` > `created_at`))),
  CONSTRAINT `ck_platform_task_hash` CHECK (regexp_like(`payload_hash`,_ascii'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_platform_task_identity` CHECK (((`id` > 0) and (`account_id` > 0) and (`created_by` > 0) and (`created_by` = `account_id`) and (`authorization_epoch` > 0))),
  CONSTRAINT `ck_platform_task_lifecycle` CHECK (((`updated_at` >= `created_at`) and (((`status` = _ascii'OPEN') and (`completed_at` is null) and (`cancelled_at` is null)) or ((`status` = _ascii'COMPLETED') and (`completed_at` is not null) and (`cancelled_at` is null) and (`completed_at` between `created_at` and `updated_at`)) or ((`status` = _ascii'CANCELLED') and (`completed_at` is null) and (`cancelled_at` is not null) and (`cancelled_at` between `created_at` and `updated_at`))))),
  CONSTRAINT `ck_platform_task_priority` CHECK ((`priority` in (_ascii'LOW',_ascii'NORMAL',_ascii'HIGH',_ascii'URGENT'))),
  CONSTRAINT `ck_platform_task_source` CHECK ((`source` in (_utf8mb4'AGENT',_utf8mb4'WORK'))),
  CONSTRAINT `ck_platform_task_status` CHECK ((`status` in (_ascii'OPEN',_ascii'COMPLETED',_ascii'CANCELLED'))),
  CONSTRAINT `ck_platform_task_title` CHECK ((char_length(trim(`title`)) between 1 and 200)),
  CONSTRAINT `ck_platform_task_work_kind` CHECK (((`task_kind` in (_utf8mb4'PERSONAL',_utf8mb4'PROJECT',_utf8mb4'GENERAL')) and (((`task_kind` = _utf8mb4'PROJECT') and (`project_id` is not null)) or ((`task_kind` <> _utf8mb4'PROJECT') and (`project_id` is null))) and ((`source` <> _utf8mb4'AGENT') or (`task_kind` = _utf8mb4'PERSONAL'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_task`
--

LOCK TABLES `un_platform_task` WRITE;
/*!40000 ALTER TABLE `un_platform_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_todo_action`
--

DROP TABLE IF EXISTS `un_platform_todo_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_todo_action` (
  `account_id` bigint unsigned NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_id` bigint unsigned NOT NULL,
  `requested_action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_version` bigint unsigned NOT NULL,
  `result_json` json NOT NULL,
  `completed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`account_id`,`idempotency_key`),
  KEY `idx_platform_todo_action_task` (`account_id`,`task_id`,`completed_at`),
  CONSTRAINT `ck_platform_todo_action_code` CHECK ((`requested_action` in (_utf8mb4'COMPLETE',_utf8mb4'REOPEN',_utf8mb4'CANCEL'))),
  CONSTRAINT `ck_platform_todo_action_identity` CHECK (((`account_id` > 0) and (`task_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_todo_action`
--

LOCK TABLES `un_platform_todo_action` WRITE;
/*!40000 ALTER TABLE `un_platform_todo_action` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_todo_action` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_work_daily_report`
--

DROP TABLE IF EXISTS `un_platform_work_daily_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_work_daily_report` (
  `id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `report_date` date NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `completed_text` varchar(4000) NOT NULL,
  `plan_text` varchar(4000) NOT NULL,
  `risk_text` varchar(4000) DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_work_report_owner_date` (`account_id`,`report_date`),
  KEY `idx_platform_work_report_owner_list` (`account_id`,`report_date` DESC,`id` DESC),
  KEY `fk_platform_work_report_project` (`project_id`),
  CONSTRAINT `fk_platform_work_report_owner` FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_platform_work_report_project` FOREIGN KEY (`project_id`) REFERENCES `un_platform_work_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_work_report_scope` CHECK (((`id` > 0) and (`account_id` > 0) and (`created_by` = `account_id`) and (`updated_by` = `account_id`))),
  CONSTRAINT `ck_platform_work_report_status` CHECK ((((`status` = _utf8mb4'DRAFT') and (`submitted_at` is null)) or ((`status` = _utf8mb4'SUBMITTED') and (`submitted_at` is not null)))),
  CONSTRAINT `ck_platform_work_report_text` CHECK (((char_length(trim(`completed_text`)) between 1 and 4000) and (char_length(trim(`plan_text`)) between 1 and 4000) and ((`risk_text` is null) or (char_length(trim(`risk_text`)) between 1 and 4000))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_work_daily_report`
--

LOCK TABLES `un_platform_work_daily_report` WRITE;
/*!40000 ALTER TABLE `un_platform_work_daily_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_work_daily_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_platform_work_project`
--

DROP TABLE IF EXISTS `un_platform_work_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_platform_work_project` (
  `id` bigint NOT NULL,
  `owner_account_id` bigint NOT NULL,
  `code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(200) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `start_date` date DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_work_project_owner_code` (`owner_account_id`,`code`),
  KEY `idx_platform_work_project_owner_status` (`owner_account_id`,`status`,`updated_at` DESC),
  CONSTRAINT `fk_platform_work_project_owner` FOREIGN KEY (`owner_account_id`) REFERENCES `un_plat_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_platform_work_project_dates` CHECK (((`due_date` is null) or (`start_date` is null) or (`due_date` >= `start_date`))),
  CONSTRAINT `ck_platform_work_project_scope` CHECK (((`id` > 0) and (`owner_account_id` > 0) and (`created_by` = `owner_account_id`) and (`updated_by` = `owner_account_id`))),
  CONSTRAINT `ck_platform_work_project_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'COMPLETED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_platform_work_project_text` CHECK ((regexp_like(`code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$') and (char_length(trim(`name`)) between 1 and 200)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_platform_work_project`
--

LOCK TABLES `un_platform_work_project` WRITE;
/*!40000 ALTER TABLE `un_platform_work_project` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_platform_work_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_sys_feature_flag`
--

DROP TABLE IF EXISTS `un_sys_feature_flag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_sys_feature_flag` (
  `id` bigint NOT NULL,
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` bigint NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `tenant_key` bigint GENERATED ALWAYS AS (coalesce(`tenant_id`,0)) STORED,
  `flag_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `rollout_percentage` int NOT NULL DEFAULT '100',
  `rules_json` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_feature_flag_scope` (`scope_type`,`scope_key`,`tenant_key`,`flag_key`),
  KEY `idx_sys_feature_flag_system` (`system_id`,`tenant_id`,`enabled`),
  CONSTRAINT `fk_sys_feature_flag_system` FOREIGN KEY (`system_id`) REFERENCES `un_plat_system` (`id`),
  CONSTRAINT `fk_sys_feature_flag_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `un_plat_tenant` (`system_id`, `id`),
  CONSTRAINT `ck_sys_feature_flag_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_sys_feature_flag_rollout` CHECK ((`rollout_percentage` between 0 and 100)),
  CONSTRAINT `ck_sys_feature_flag_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_key` = 0) and (`system_id` is null) and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_key` = `system_id`) and (`system_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_sys_feature_flag`
--

LOCK TABLES `un_sys_feature_flag` WRITE;
/*!40000 ALTER TABLE `un_sys_feature_flag` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_sys_feature_flag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_sys_idempotency`
--

DROP TABLE IF EXISTS `un_sys_idempotency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_sys_idempotency` (
  `id` bigint NOT NULL,
  `scope_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_http_status` int DEFAULT NULL,
  `response_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_body` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `locked_until` datetime(3) DEFAULT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_idempotency_scope_key` (`scope_type`,`scope_key`,`idempotency_key`),
  KEY `idx_sys_idempotency_expiry` (`expires_at`),
  CONSTRAINT `ck_sys_idempotency_status` CHECK ((`status` in (_utf8mb4'PROCESSING',_utf8mb4'COMPLETED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_sys_idempotency`
--

LOCK TABLES `un_sys_idempotency` WRITE;
/*!40000 ALTER TABLE `un_sys_idempotency` DISABLE KEYS */;
INSERT INTO `un_sys_idempotency` VALUES (2085920203700731906,'PLATFORM_ADMIN','2085917350597300225:SYSTEM_CREATE:new','ae728d0f-42c6-4844-9fce-8d22f6760609','a343b7aab2a62860c81a7a0b55d2a1507ea45a8cf451d88618e0398678fc0fb3','COMPLETED',200,'OK','{\"id\":\"2085920203721703426\",\"code\":\"cycle118_acceptance\",\"name\":\"Cycle118 ','2026-08-08 02:47:05.741','2026-08-09 02:45:05.741','2026-08-08 02:45:05.741','2026-08-08 02:45:06.247');
/*!40000 ALTER TABLE `un_sys_idempotency` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_sys_job`
--

DROP TABLE IF EXISTS `un_sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_sys_job` (
  `id` bigint NOT NULL,
  `job_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `requested_by` bigint DEFAULT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress_percent` int NOT NULL DEFAULT '0',
  `input_json` json DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `result_file_id` bigint DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '3',
  `available_at` datetime(3) NOT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `last_error` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_job_queue` (`status`,`available_at`,`lease_until`),
  KEY `idx_sys_job_owner` (`owner_type`,`owner_id`,`created_at`),
  CONSTRAINT `ck_sys_job_attempts` CHECK (((`attempt_count` >= 0) and (`max_attempts` > 0))),
  CONSTRAINT `ck_sys_job_progress` CHECK ((`progress_percent` between 0 and 100)),
  CONSTRAINT `ck_sys_job_status` CHECK ((`status` in (_utf8mb4'QUEUED',_utf8mb4'RUNNING',_utf8mb4'SUCCEEDED',_utf8mb4'PARTIAL',_utf8mb4'FAILED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_sys_job`
--

LOCK TABLES `un_sys_job` WRITE;
/*!40000 ALTER TABLE `un_sys_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_sys_outbox_event`
--

DROP TABLE IF EXISTS `un_sys_outbox_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_sys_outbox_event` (
  `id` bigint NOT NULL,
  `event_type` varchar(96) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_version` int NOT NULL,
  `aggregate_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `system_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `dedupe_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` json NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `available_at` datetime(3) NOT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `last_error` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_outbox_dedupe` (`dedupe_key`),
  KEY `idx_sys_outbox_dispatch` (`status`,`available_at`,`lease_until`),
  CONSTRAINT `ck_sys_outbox_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'PUBLISHED',_utf8mb4'DEAD')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_sys_outbox_event`
--

LOCK TABLES `un_sys_outbox_event` WRITE;
/*!40000 ALTER TABLE `un_sys_outbox_event` DISABLE KEYS */;
INSERT INTO `un_sys_outbox_event` VALUES (2085920205793689602,'PLATFORM_SYSTEM_CREATED',1,'PLATFORM_SYSTEM','2085920203721703426',NULL,NULL,'991bebc6df7ff11d2b5ef8d8bd729f96c95e1d7515771118e3bf0a9e1ed7f37d','{\"after\": {\"id\": \"2085920203721703426\", \"code\": \"cycle118_acceptance\", \"name\": \"Cycle118 \裓閈蔦障低砛", \"status\": \"ACTIVE\", \"version\": \"1\", \"createdAt\": \"2026-08-08T02:45:05.748370576\", \"tenantMode\": \"SINGLE\", \"description\": \"\覾肻覾赲譢頫终功\能、\衆診能、可穃肻蝄蔦衆診覾牖指碶裓萘穃", \"ownerAccountId\": \"2085917350597300225\"}, \"action\": \"PLATFORM_SYSTEM_CREATED\", \"requestId\": \"923f885d-ce79-463d-9131-ba987c3611de\", \"actorAccountId\": \"2085917350597300225\"}','e5bb20293a8042a2a5ee02407766028c','PENDING','2026-08-08 02:45:06.243',NULL,0,NULL,NULL,'2026-08-08 02:45:06.243','2026-08-08 02:45:06.243');
/*!40000 ALTER TABLE `un_sys_outbox_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_todo_action_log`
--

DROP TABLE IF EXISTS `un_todo_action_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_todo_action_log` (
  `id` bigint unsigned NOT NULL,
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `todo_item_id` bigint unsigned NOT NULL,
  `recipient_member_id` bigint unsigned NOT NULL,
  `actor_member_id` bigint unsigned NOT NULL,
  `caller_idempotency_key` varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_id` varchar(200) NOT NULL,
  `source_version` bigint unsigned NOT NULL,
  `requested_action` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_code` varchar(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_message` varchar(500) DEFAULT NULL,
  `request_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_todo_action_log_id` (`id`),
  UNIQUE KEY `uk_todo_action_idempotency` (`system_id`,`tenant_id`,`actor_member_id`,`caller_idempotency_key`),
  KEY `idx_todo_action_item` (`system_id`,`tenant_id`,`todo_item_id`,`created_at`,`id`),
  KEY `fk_todo_action_item` (`system_id`,`tenant_id`,`todo_item_id`,`recipient_member_id`),
  CONSTRAINT `fk_todo_action_item` FOREIGN KEY (`system_id`, `tenant_id`, `todo_item_id`, `recipient_member_id`) REFERENCES `un_todo_item` (`system_id`, `tenant_id`, `id`, `recipient_member_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_todo_action_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`todo_item_id` > 0) and (`recipient_member_id` > 0) and (`actor_member_id` > 0) and (`actor_member_id` = `recipient_member_id`) and (`source_version` > 0) and (`version` > 0))),
  CONSTRAINT `ck_todo_action_source` CHECK ((((`source_type` = _utf8mb4'WORK_TASK') and (`requested_action` = _utf8mb4'COMPLETE')) or ((`source_type` = _utf8mb4'FLOW_APPROVAL') and (`requested_action` in (_utf8mb4'APPROVE',_utf8mb4'REJECT'))) or ((`source_type` = _utf8mb4'EVENT_MESSAGE') and (`requested_action` = _utf8mb4'MARK_READ')))),
  CONSTRAINT `ck_todo_action_state` CHECK ((((`status` = _ascii'PROCESSING') and (`result_code` is null) and (`result_message` is null) and (`completed_at` is null)) or ((`status` = _ascii'COMPLETED') and (`result_code` is not null) and (`completed_at` is not null) and (`result_code` in (_ascii'SUCCESS',_ascii'STALE',_ascii'DENIED',_ascii'CONFLICT',_ascii'FAILED')) and (`completed_at` >= `created_at`)))),
  CONSTRAINT `ck_todo_action_status` CHECK ((`status` in (_ascii'PROCESSING',_ascii'COMPLETED'))),
  CONSTRAINT `ck_todo_action_text` CHECK (((char_length(trim(`caller_idempotency_key`)) between 1 and 200) and (char_length(trim(`source_id`)) between 1 and 200) and (char_length(trim(`request_id`)) between 1 and 128) and (char_length(trim(`trace_id`)) between 1 and 128) and ((`result_message` is null) or (char_length(trim(`result_message`)) between 1 and 500))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_todo_action_log`
--

LOCK TABLES `un_todo_action_log` WRITE;
/*!40000 ALTER TABLE `un_todo_action_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_todo_action_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_todo_item`
--

DROP TABLE IF EXISTS `un_todo_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_todo_item` (
  `id` bigint unsigned NOT NULL,
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `recipient_member_id` bigint unsigned NOT NULL,
  `source_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_id` varchar(200) NOT NULL,
  `source_version` bigint unsigned NOT NULL,
  `action_scope` varchar(200) NOT NULL,
  `category` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `priority` smallint unsigned NOT NULL,
  `title` varchar(500) NOT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `route_hint` varchar(500) NOT NULL,
  `available_actions` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `represented_member_id` bigint unsigned DEFAULT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `close_reason` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_todo_item_id` (`id`),
  UNIQUE KEY `uk_todo_item_identity` (`system_id`,`tenant_id`,`recipient_member_id`,`source_type`,`source_id`,`action_scope`),
  UNIQUE KEY `uk_todo_item_scope_recipient` (`system_id`,`tenant_id`,`id`,`recipient_member_id`),
  KEY `idx_todo_item_recipient_status` (`system_id`,`tenant_id`,`recipient_member_id`,`status`,`priority`,`due_at`,`created_at`,`id`),
  KEY `idx_todo_item_recipient_category` (`system_id`,`tenant_id`,`recipient_member_id`,`category`,`status`,`priority`,`due_at`,`created_at`,`id`),
  KEY `idx_todo_item_source` (`system_id`,`tenant_id`,`source_type`,`source_id`,`source_version`),
  CONSTRAINT `ck_todo_item_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`recipient_member_id` > 0) and (`source_version` > 0))),
  CONSTRAINT `ck_todo_item_priority` CHECK ((`priority` <= 999)),
  CONSTRAINT `ck_todo_item_source` CHECK ((((`source_type` = _utf8mb4'WORK_TASK') and (`category` = _utf8mb4'TASK') and (`available_actions` = _utf8mb4'COMPLETE') and (`represented_member_id` is null)) or ((`source_type` = _utf8mb4'FLOW_APPROVAL') and (`category` = _utf8mb4'APPROVAL') and (`available_actions` = _utf8mb4'APPROVE,REJECT') and (`represented_member_id` is not null) and (`represented_member_id` > 0)) or ((`source_type` = _utf8mb4'EVENT_MESSAGE') and (`category` in (_utf8mb4'REMINDER',_utf8mb4'CC')) and (`available_actions` = _utf8mb4'MARK_READ') and (`represented_member_id` is null) and (`action_scope` = _utf8mb4'MARK_READ') and regexp_like(`source_id`,_utf8mb4'^[1-9][0-9]{0,18}$') and ((char_length(`source_id`) < 19) or (`source_id` <= _utf8mb4'9223372036854775807'))))),
  CONSTRAINT `ck_todo_item_state` CHECK (((`updated_at` >= `created_at`) and (`version` > 0) and (((`status` = _ascii'OPEN') and (`close_reason` is null) and (`closed_at` is null)) or ((`status` = _ascii'CLOSED') and (`close_reason` is not null) and (`closed_at` is not null) and (`close_reason` in (_ascii'SOURCE_STALE',_ascii'SOURCE_COMPLETED',_ascii'SOURCE_MISSING',_ascii'RECIPIENT_INELIGIBLE',_ascii'ACTION_COMPLETED')) and (`closed_at` between `created_at` and `updated_at`))))),
  CONSTRAINT `ck_todo_item_status` CHECK ((`status` in (_ascii'OPEN',_ascii'CLOSED'))),
  CONSTRAINT `ck_todo_item_text` CHECK (((char_length(trim(`source_id`)) between 1 and 200) and (char_length(trim(`action_scope`)) between 1 and 200) and (char_length(trim(`title`)) between 1 and 500) and (char_length(trim(`route_hint`)) between 1 and 500)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_todo_item`
--

LOCK TABLES `un_todo_item` WRITE;
/*!40000 ALTER TABLE `un_todo_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_todo_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_ai_draft_execution`
--

DROP TABLE IF EXISTS `un_work_ai_draft_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_ai_draft_execution` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `proposal_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `turn_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` bigint NOT NULL,
  `authorization_epoch` bigint unsigned NOT NULL,
  `operation` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_version_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `provider_version` bigint unsigned NOT NULL,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `prepare_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `prepare_trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execute_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execute_trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_json` mediumtext,
  `created_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_work_ai_draft_execution_id` (`id`),
  UNIQUE KEY `uk_work_ai_draft_execution_proposal` (`system_id`,`tenant_id`,`member_id`,`proposal_id`),
  UNIQUE KEY `uk_work_ai_draft_execution_key` (`system_id`,`tenant_id`,`member_id`,`idempotency_key`),
  KEY `fk_work_ai_draft_execution_member_tenant` (`system_id`,`member_id`,`tenant_id`),
  CONSTRAINT `fk_work_ai_draft_execution_member_tenant` FOREIGN KEY (`system_id`, `member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_work_ai_draft_execution_hash` CHECK (regexp_like(`payload_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `ck_work_ai_draft_execution_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`member_id` > 0) and (`account_id` > 0) and (`authorization_epoch` > 0) and (`policy_version_id` > 0) and (`provider_id` > 0) and (`provider_version` >= 0))),
  CONSTRAINT `ck_work_ai_draft_execution_operation` CHECK ((`operation` in (_utf8mb4'WORK_TASK_DRAFT',_utf8mb4'WORK_DAILY_REPORT_DRAFT'))),
  CONSTRAINT `ck_work_ai_draft_execution_result` CHECK (((((`result_json` is null) and (`completed_at` is null)) or ((`result_json` is not null) and (`completed_at` is not null))) and ((`result_json` is null) or json_valid(`result_json`)))),
  CONSTRAINT `ck_work_ai_draft_execution_time` CHECK (((`expires_at` > `created_at`) and ((`completed_at` is null) or (`completed_at` >= `created_at`)))),
  CONSTRAINT `ck_work_ai_draft_execution_tokens` CHECK ((regexp_like(`proposal_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`session_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`turn_id`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$') and regexp_like(`idempotency_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_ai_draft_execution`
--

LOCK TABLES `un_work_ai_draft_execution` WRITE;
/*!40000 ALTER TABLE `un_work_ai_draft_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_ai_draft_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_configuration`
--

DROP TABLE IF EXISTS `un_work_configuration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_configuration` (
  `id` bigint unsigned NOT NULL,
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `snapshot_json` json NOT NULL,
  `rollback_from_revision` bigint unsigned DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `published_by` bigint unsigned DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`status` = _ascii'PUBLISHED') then 1 else NULL end)) STORED,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_work_configuration_id` (`id`),
  UNIQUE KEY `uk_work_configuration_revision` (`system_id`,`tenant_id`,`revision`),
  UNIQUE KEY `uk_work_configuration_active` (`system_id`,`tenant_id`,`active_marker`),
  KEY `idx_work_configuration_history` (`system_id`,`tenant_id`,`revision`,`status`),
  CONSTRAINT `ck_work_configuration_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`revision` > 0) and (`created_by` > 0) and (`version` > 0))),
  CONSTRAINT `ck_work_configuration_publish` CHECK ((((`status` = _utf8mb4'DRAFT') and (`published_by` is null) and (`published_at` is null)) or ((`status` in (_utf8mb4'PUBLISHED',_utf8mb4'RETIRED')) and (`published_by` is not null) and (`published_at` is not null) and (`published_at` >= `created_at`)))),
  CONSTRAINT `ck_work_configuration_rollback` CHECK (((`rollback_from_revision` is null) or (`rollback_from_revision` > 0))),
  CONSTRAINT `ck_work_configuration_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'RETIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_configuration`
--

LOCK TABLES `un_work_configuration` WRITE;
/*!40000 ALTER TABLE `un_work_configuration` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_configuration` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_daily_report`
--

DROP TABLE IF EXISTS `un_work_daily_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_daily_report` (
  `id` bigint NOT NULL,
  `system_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `author_member_id` bigint NOT NULL,
  `work_date` date NOT NULL,
  `completed_work` varchar(4000) NOT NULL,
  `planned_work` varchar(4000) NOT NULL,
  `blockers` varchar(4000) DEFAULT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_work_daily_report_id` (`id`),
  UNIQUE KEY `uk_work_daily_report_author_date` (`system_id`,`tenant_id`,`author_member_id`,`work_date`),
  KEY `idx_work_daily_report_list` (`system_id`,`tenant_id`,`work_date`,`updated_at`,`id`),
  KEY `idx_work_daily_report_member_list` (`system_id`,`tenant_id`,`author_member_id`,`work_date`,`status`,`updated_at`,`id`),
  KEY `idx_work_daily_report_status_list` (`system_id`,`tenant_id`,`status`,`work_date`,`updated_at`,`id`),
  KEY `fk_work_daily_report_author` (`system_id`,`author_member_id`,`tenant_id`),
  CONSTRAINT `fk_work_daily_report_author` FOREIGN KEY (`system_id`, `author_member_id`, `tenant_id`) REFERENCES `un_plat_member_tenant` (`system_id`, `member_id`, `tenant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_work_daily_report_blockers` CHECK (((`blockers` is null) or (char_length(trim(`blockers`)) between 1 and 4000))),
  CONSTRAINT `ck_work_daily_report_completed` CHECK ((char_length(trim(`completed_work`)) between 1 and 4000)),
  CONSTRAINT `ck_work_daily_report_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`author_member_id` > 0))),
  CONSTRAINT `ck_work_daily_report_planned` CHECK ((char_length(trim(`planned_work`)) between 1 and 4000)),
  CONSTRAINT `ck_work_daily_report_state` CHECK (((`updated_at` >= `created_at`) and (`version` > 0) and (((`status` = _utf8mb4'DRAFT') and (`submitted_at` is null)) or ((`status` = _utf8mb4'SUBMITTED') and (`submitted_at` is not null) and (`submitted_at` >= `created_at`) and (`submitted_at` <= `updated_at`))))),
  CONSTRAINT `ck_work_daily_report_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_daily_report`
--

LOCK TABLES `un_work_daily_report` WRITE;
/*!40000 ALTER TABLE `un_work_daily_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_daily_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_project`
--

DROP TABLE IF EXISTS `un_work_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_project` (
  `id` bigint unsigned NOT NULL,
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `creator_member_id` bigint unsigned NOT NULL,
  `title` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`id`),
  UNIQUE KEY `uk_work_project_id` (`id`),
  KEY `idx_work_project_list` (`system_id`,`tenant_id`,`status`,`updated_at`,`id`),
  KEY `idx_work_project_creator` (`system_id`,`tenant_id`,`creator_member_id`,`updated_at`,`id`),
  CONSTRAINT `ck_work_project_description` CHECK (((`description` is null) or (char_length(trim(`description`)) between 1 and 2000))),
  CONSTRAINT `ck_work_project_identity` CHECK (((`id` > 0) and (`system_id` > 0) and (`tenant_id` > 0) and (`creator_member_id` > 0))),
  CONSTRAINT `ck_work_project_state` CHECK (((`updated_at` >= `created_at`) and (`version` > 0))),
  CONSTRAINT `ck_work_project_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_work_project_title` CHECK ((char_length(trim(`title`)) between 1 and 200))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_project`
--

LOCK TABLES `un_work_project` WRITE;
/*!40000 ALTER TABLE `un_work_project` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_project_member`
--

DROP TABLE IF EXISTS `un_work_project_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_project_member` (
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `project_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `role` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `joined_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`project_id`,`member_id`),
  KEY `idx_work_project_member_access` (`system_id`,`tenant_id`,`member_id`,`status`,`project_id`),
  KEY `idx_work_project_member_owner` (`system_id`,`tenant_id`,`project_id`,`status`,`role`,`member_id`),
  CONSTRAINT `fk_work_project_member_project` FOREIGN KEY (`system_id`, `tenant_id`, `project_id`) REFERENCES `un_work_project` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_work_project_member_identity` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`project_id` > 0) and (`member_id` > 0))),
  CONSTRAINT `ck_work_project_member_role` CHECK ((`role` in (_utf8mb4'OWNER',_utf8mb4'MEMBER'))),
  CONSTRAINT `ck_work_project_member_state` CHECK (((`updated_at` >= `joined_at`) and (`version` > 0))),
  CONSTRAINT `ck_work_project_member_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REMOVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_project_member`
--

LOCK TABLES `un_work_project_member` WRITE;
/*!40000 ALTER TABLE `un_work_project_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_project_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_runtime_field_value`
--

DROP TABLE IF EXISTS `un_work_runtime_field_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_runtime_field_value` (
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `object_type` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `object_id` bigint unsigned NOT NULL,
  `configuration_revision` bigint unsigned NOT NULL,
  `value_json` json NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint unsigned NOT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`object_type`,`object_id`),
  KEY `idx_work_runtime_configuration` (`system_id`,`tenant_id`,`configuration_revision`,`object_type`),
  CONSTRAINT `fk_work_runtime_configuration` FOREIGN KEY (`system_id`, `tenant_id`, `configuration_revision`) REFERENCES `un_work_configuration` (`system_id`, `tenant_id`, `revision`) ON DELETE RESTRICT,
  CONSTRAINT `ck_work_runtime_identity` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`object_id` > 0) and (`configuration_revision` > 0) and (`updated_by` > 0) and (`version` > 0))),
  CONSTRAINT `ck_work_runtime_type` CHECK ((`object_type` in (_utf8mb4'PROJECT_TASK',_utf8mb4'ORDINARY_TASK',_utf8mb4'DAILY_REPORT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_runtime_field_value`
--

LOCK TABLES `un_work_runtime_field_value` WRITE;
/*!40000 ALTER TABLE `un_work_runtime_field_value` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_runtime_field_value` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_task`
--

DROP TABLE IF EXISTS `un_work_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_task` (
  `id` bigint unsigned NOT NULL,
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `creator_member_id` bigint unsigned NOT NULL,
  `assignee_member_id` bigint unsigned NOT NULL,
  `project_id` bigint unsigned DEFAULT NULL,
  `title` varchar(500) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `reminder_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_task_scope_id` (`system_id`,`tenant_id`,`id`),
  KEY `idx_work_task_assignee_state` (`system_id`,`tenant_id`,`assignee_member_id`,`status`,`updated_at`),
  KEY `idx_work_task_creator_state` (`system_id`,`tenant_id`,`creator_member_id`,`status`,`updated_at`),
  KEY `idx_work_task_tenant_updated` (`system_id`,`tenant_id`,`updated_at`),
  KEY `idx_work_task_project_state` (`system_id`,`tenant_id`,`project_id`,`status`,`updated_at`,`id`),
  KEY `idx_work_task_project_due` (`system_id`,`tenant_id`,`project_id`,`due_at`,`id`),
  KEY `idx_work_task_due_window` (`system_id`,`tenant_id`,`due_at`,`status`,`updated_at`,`id`),
  KEY `idx_work_task_reminder_window` (`system_id`,`tenant_id`,`reminder_at`,`status`,`id`),
  CONSTRAINT `fk_work_task_project` FOREIGN KEY (`system_id`, `tenant_id`, `project_id`) REFERENCES `un_work_project` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_work_task_members` CHECK (((`creator_member_id` > 0) and (`assignee_member_id` > 0))),
  CONSTRAINT `chk_work_task_scope` CHECK (((`system_id` > 0) and (`tenant_id` > 0))),
  CONSTRAINT `chk_work_task_status` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'COMPLETED'))),
  CONSTRAINT `chk_work_task_title` CHECK ((char_length(trim(`title`)) between 1 and 500)),
  CONSTRAINT `chk_work_task_version` CHECK ((`version` > 0)),
  CONSTRAINT `ck_work_task_description` CHECK (((`description` is null) or (char_length(trim(`description`)) between 1 and 2000))),
  CONSTRAINT `ck_work_task_reminder_schedule` CHECK (((`reminder_at` is null) or ((`status` = _utf8mb4'OPEN') and ((`due_at` is null) or (`reminder_at` <= `due_at`)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_task`
--

LOCK TABLES `un_work_task` WRITE;
/*!40000 ALTER TABLE `un_work_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `un_work_task_reminder`
--

DROP TABLE IF EXISTS `un_work_task_reminder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `un_work_task_reminder` (
  `system_id` bigint unsigned NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `task_id` bigint unsigned NOT NULL,
  `generation` int unsigned NOT NULL,
  `scheduled_at` datetime(6) NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attempt_count` int unsigned NOT NULL,
  `lease_owner` varchar(160) DEFAULT NULL,
  `lease_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `failed_at` datetime(6) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `failure_message` varchar(500) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  PRIMARY KEY (`system_id`,`tenant_id`,`task_id`,`generation`),
  UNIQUE KEY `uk_work_task_reminder_generation` (`system_id`,`tenant_id`,`task_id`,`generation`),
  KEY `idx_work_task_reminder_due` (`status`,`scheduled_at`,`task_id`,`generation`,`system_id`,`tenant_id`),
  KEY `idx_work_task_reminder_lease` (`status`,`lease_expires_at`,`task_id`,`generation`,`system_id`,`tenant_id`),
  CONSTRAINT `fk_work_task_reminder_task` FOREIGN KEY (`system_id`, `tenant_id`, `task_id`) REFERENCES `un_work_task` (`system_id`, `tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_work_task_reminder_identity` CHECK (((`system_id` > 0) and (`tenant_id` > 0) and (`task_id` > 0) and (`generation` > 0))),
  CONSTRAINT `ck_work_task_reminder_lease` CHECK ((((`status` = _utf8mb4'PROCESSING') and (`attempt_count` > 0) and (`lease_owner` is not null) and (char_length(trim(`lease_owner`)) between 1 and 160) and regexp_like(`lease_token_hash`,_utf8mb4'^[0-9a-f]{64}$') and (`lease_expires_at` is not null) and (`lease_expires_at` > `updated_at`)) or ((`status` <> _utf8mb4'PROCESSING') and (`lease_owner` is null) and (`lease_token_hash` is null) and (`lease_expires_at` is null)))),
  CONSTRAINT `ck_work_task_reminder_state` CHECK ((((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING')) and (`sent_at` is null) and (`failed_at` is null) and (`cancelled_at` is null) and (`failure_code` is null) and (`failure_message` is null)) or ((`status` = _utf8mb4'SENT') and (`attempt_count` > 0) and (`sent_at` between `created_at` and `updated_at`) and (`failed_at` is null) and (`cancelled_at` is null) and (`failure_code` is null) and (`failure_message` is null)) or ((`status` = _utf8mb4'CANCELLED') and (`cancelled_at` between `created_at` and `updated_at`) and (`sent_at` is null) and (`failed_at` is null) and (`failure_code` is null) and (`failure_message` is null)) or ((`status` = _utf8mb4'FAILED') and (`attempt_count` > 0) and (`failed_at` between `created_at` and `updated_at`) and (`sent_at` is null) and (`cancelled_at` is null) and regexp_like(`failure_code`,_utf8mb4'^[A-Z][A-Z0-9_]{0,63}$') and (char_length(trim(`failure_message`)) between 1 and 500)))),
  CONSTRAINT `ck_work_task_reminder_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'SENT',_utf8mb4'CANCELLED',_utf8mb4'FAILED'))),
  CONSTRAINT `ck_work_task_reminder_time` CHECK (((`scheduled_at` >= `created_at`) and (`updated_at` >= `created_at`) and (`version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `un_work_task_reminder`
--

LOCK TABLES `un_work_task_reminder` WRITE;
/*!40000 ALTER TABLE `un_work_task_reminder` DISABLE KEYS */;
/*!40000 ALTER TABLE `un_work_task_reminder` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'examine2'
--

--
-- Dumping routines for database 'examine2'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-08  3:36:28
