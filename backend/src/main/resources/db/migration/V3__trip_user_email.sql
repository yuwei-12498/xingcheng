ALTER TABLE `trip_user`
  ADD COLUMN `email` VARCHAR(128) NULL AFTER `username`,
  ADD UNIQUE KEY `uk_trip_user_email` (`email`);
