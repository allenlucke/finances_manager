CREATE TABLE users (
	id				INT GENERATED ALWAYS AS IDENTITY,
	first_name		VARCHAR2(20) NOT NULL,
    last_name		VARCHAR2(20) NOT NULL,
	created			TIMESTAMP DEFAULT SYSTIMESTAMP,
    last_active		TIMESTAMP DEFAULT SYSTIMESTAMP,
	user_name		VARCHAR2(20) NOT NULL,
    password		VARCHAR2(20) NOT NULL,
    email			VARCHAR2(40) NOT NULL,
    sec_lvl			INT DEFAULT 1 NOT NULL,
    is_active		NUMBER(1) DEFAULT 1 check (is_active in ( 0, 1, NULL ))
);

CREATE TABLE income (
	id				INT GENERATED ALWAYS AS IDENTITY ,
    name			VARCHAR2(20) NOT NULL,
    recieved 		NUMBER(1) DEFAULT 0 check (recieved in ( 0, 1, NULL )) ,
    due_on			TIMESTAMP,
    recieved_on		TIMESTAMP,
    recurring 		NUMBER(1) DEFAULT 1 check (recurring in ( 0, 1, NULL )) ,
    amount_expected	NUMBER(12,2) DEFAULT 0.00,
    amount_actual	NUMBER(12,2) DEFAULT 0.00,
    users_id		INT NOT NULL,
	--References users.id
	category_id		INT,
	--References income_categories.id
	accounts_id		INT
	--References accounts.id
);

CREATE TABLE expenses (
	id				INT GENERATED ALWAYS AS IDENTITY,
    name			VARCHAR2(20) NOT NULL,
    paid			NUMBER(1) DEFAULT 0 check (paid in ( 0, 1, NULL )) ,
    due_by			TIMESTAMP,
    paid_on			TIMESTAMP,
    recurring 		NUMBER(1) DEFAULT 1 check (recurring in ( 0, 1, NULL )) ,
    amount_due		NUMBER(12,2) DEFAULT 0.00,
    amount_paid		NUMBER(12,2) DEFAULT 0.00,
    users_id        INT NOT NULL,
    --References users.id 
    category_id		INT,
    --References expenses_categories.id
    accounts_id		INT
	--References accounts.id
);

CREATE TABLE expenses_categories (
	id				INT GENERATED ALWAYS AS IDENTITY,
    name			VARCHAR2(20) NOT NULL,
    due_by			TIMESTAMP,
    paid_on			TIMESTAMP,
    amount_paid		NUMBER(12,2) DEFAULT 0.00,
    recurring 		NUMBER(1) DEFAULT 1 check (recurring in ( 0, 1, NULL )) ,
    amount_due		NUMBER(12,2) DEFAULT 0.00,
    users_id        INT NOT NULL,
    --References users.id
    period_id		INT
    --References period.id
); 

CREATE TABLE income_categories (
	id				INT GENERATED ALWAYS AS IDENTITY,
    name			VARCHAR2(20) NOT NULL,
    due_by			TIMESTAMP,
    paid_on			TIMESTAMP,
    amount_paid		NUMBER(12,2) DEFAULT 0.00,
    recurring 		NUMBER(1) DEFAULT 1 check (recurring in ( 0, 1, NULL )) ,
    amount_due		NUMBER(12,2) DEFAULT 0.00,
    users_id        INT NOT NULL,
    --References users.id
    period_id		INT
    --References period.id
);

--manages period data
CREATE TABLE period (
	id				INT GENERATED ALWAYS AS IDENTITY,
	start_date		TIMESTAMP,
	end_date		TIMESTAMP,
	users_id		INT NOT NULL
	--References users.id
);

--manages individual account
CREATE TABLE account (
	id				INT GENERATED ALWAYS AS IDENTITY,
	name			VARCHAR2(20) NOT NULL,
	balance			NUMBER(12,2) DEFAULT 0.00,
	created			TIMESTAMP DEFAULT SYSTIMESTAMP,
    last_active		TIMESTAMP DEFAULT SYSTIMESTAMP,
    users_id		INT NOT NULL
	--References users.id
);
