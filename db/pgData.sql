INSERT INTO "users"
    ("firstName", "lastName", "username", "password", "securityLevel", "email", "role")
VALUES
    ( 'dev', 'user', 'DEVUSER', '1234', 99, 'devuser@gmail.com', 'DevAdmin'),
    ( 'test', 'user1', 'TSTUSER1', '1234', 1, 'tstuser1@gmail.com', 'Test User'),
	( 'test', 'user2', 'TSTUSER2', '1234', 2, 'tstuser2@gmail.com', 'Test User');
	
INSERT INTO "period"
	("name", "startDate", "endDate", "users_id")
VALUES
	('May-2021', '05-01-2021', '05-31-2021', 1),
	('June-2021', '06-01-2021', '06-30-2021', 1),
	('July-2021', '07-01-2021', '07-31-2021', 1);