CREATE TABLE message
  (
     message_id       VARCHAR(200) NOT NULL,
     sender           VARCHAR(200) NOT NULL,
     description      VARCHAR(200) NULL,
     recipients       VARCHAR(200) NOT NULL,
     ip               VARCHAR(15) NOT NULL,
     correlation_id   VARCHAR(200) NOT NULL,
     cluster_id       VARCHAR(20) NOT NULL,
     text             VARCHAR(4000) NOT NULL,
     PRIMARY KEY (message_id)
  )
;