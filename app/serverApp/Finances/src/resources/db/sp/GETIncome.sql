create or replace NONEDITIONABLE PROCEDURE GETIncome
(
	users_id		IN INT,
    category_id 	IN INT,
    accounts_id     IN INT,
    name            IN VARCHAR2,
    recieved        IN NUMBER,
    recurring       IN NUMBER,
    amount_expected IN NUMBER,
    amount_actual   IN NUMBER,
    period          IN INT,
    startdate       IN TIMESTAMP,
    enddate         IN TIMESTAMP,
	P_CURSOR		IN OUT SYS_REFCURSOR
)
AS
    --users_id variable/querystring declaration
    V_USRID INT DEFAULT 1;
--    USRSTR  VARCHAR2 (100) DEFAULT '';

    --category_id  variable/querystring declaration
    V_CATID INT;
    CATSTR  VARCHAR2 (50) DEFAULT '';

    --accounts_id  variable/querystring declaration
    V_ACTID INT;
    ACTSTR  VARCHAR2 (50) DEFAULT '';

    --income_name variable/querystring declaration
	V_INNAM VARCHAR2 (40) DEFAULT '';
    INCSTR  VARCHAR2 (100) DEFAULT '';
    
    --recieved variable/querystring declaration
	V_RECVD NUMBER DEFAULT 0;
    RECSTR  VARCHAR2 (50) DEFAULT '';
    
    --recurring variable/querystring declaration
    V_RCURG NUMBER DEFAULT 1;
    RCGSTR  VARCHAR2 (50) DEFAULT '';
    
    --amount_expected variable/querystring declaration
	V_AMEXP NUMBER (12,2) DEFAULT 0.00;
    AEXSTR  VARCHAR2 (80) DEFAULT '';
    
    --amount_actual variable/querystring declaration
	V_AMACT NUMBER (12,2) DEFAULT 0.00;
    AACSTR  VARCHAR2 (80) DEFAULT '';
    
    --period variable/querystring declaration
	V_PERIO INT;
    PERSTR  VARCHAR2 (50) DEFAULT '';
    
    --startdate and endate variable declaration
    V_STDAT TIMESTAMP := TO_TIMESTAMP('9999-12-31-23.59.59.999999', 'YYYY-MM-DD-HH24.MI.SS.FF6');
    V_ENDAT TIMESTAMP := TO_TIMESTAMP('9999-12-31-23.59.59.999999', 'YYYY-MM-DD-HH24.MI.SS.FF6');
	SQLSTR VARCHAR2 (5000) DEFAULT '';

BEGIN
    --users_id variable assignment
    IF(RTRIM(users_id)IS NOT NULL)THEN
		V_USRID := users_id;
	END IF; 

    --category_id variable/querystring assignment
	IF (RTRIM(category_id)IS NOT NULL)THEN
		V_CATID := category_id;
	END IF;
    IF (RTRIM(category_id)IS NOT NULL)THEN
        CATSTR := ' AND income.category_id = ''' || V_CATID || ''' ';
	END IF;

    --accounts_id variable/querystring assignment
	IF (RTRIM(accounts_id)IS NOT NULL)THEN
		V_ACTID := accounts_id;
	END IF;
    IF (RTRIM(accounts_id)IS NOT NULL)THEN
        ACTSTR := ' AND income.accounts_id = ''' || V_ACTID || ''' ';
	END IF;

    --income_name variable/querystring assignment
	IF (RTRIM(name)IS NOT NULL)THEN
		V_INNAM := name;
	END IF;
    IF (RTRIM(name)IS NOT NULL)THEN
        INCSTR := ' AND income.name = ''' || V_INNAM || ''' ';
	END IF;
    
    --recieved variable/querystring assignment
	IF(RTRIM(recieved)IS NOT NULL)THEN
		V_RECVD := recieved;
	END IF;
    IF (RTRIM(recieved)IS NOT NULL AND RTRIM(recieved) != 2 )THEN
        RECSTR := ' AND income.recieved = ''' || V_RECVD || ''' ';
	END IF;
    
    --recurring variable/querystring assignment
	IF(RTRIM(recurring)IS NOT NULL)THEN  
		V_RCURG := recurring;
	END IF;
    IF (RTRIM(recurring)IS NOT NULL AND RTRIM(recurring) != 2 )THEN
        RCGSTR := ' AND income.recurring = ''' || V_RCURG || ''' ';
	END IF;
    
    --amount_expected variable/querystring assignment
    IF(RTRIM(amount_expected)IS NOT NULL)THEN
		V_AMEXP := amount_expected;
	END IF;
    IF (RTRIM(amount_expected)IS NOT NULL)THEN
        AEXSTR := ' AND income.amount_expected = ''' || V_AMEXP || ''' ';
	END IF;
    
    --amount_actual variable/querystring assignment
    IF(RTRIM(amount_actual)IS NOT NULL)THEN
		V_AMACT := amount_actual;
	END IF;
    IF (RTRIM(amount_actual)IS NOT NULL)THEN
        AACSTR := ' AND income.amount_actual = ''' || V_AMACT || ''' ';
	END IF;
    
    --period variable/querystring assignment
	IF (RTRIM(period)IS NOT NULL)THEN
		V_PERIO := period;
	END IF;
    IF (RTRIM(period)IS NOT NULL)THEN
        PERSTR := ' AND period.id = ''' || V_PERIO || ''' ';
	END IF;
    
    --startdate variable assignment
    IF (RTRIM(startdate)IS NOT NULL)THEN
		V_STDAT := startdate;
	ELSE
		V_STDAT := SYSTIMESTAMP - INTERVAL '300' DAY;
	END IF;
    
    --enddate variable assignment  
    IF(RTRIM(enddate)IS NOT NULL)THEN
		V_ENDAT := enddate;
	ELSE
		V_ENDAT := SYSTIMESTAMP + INTERVAL '300' DAY;
	END IF;

	
	SQLSTR := SQLSTR || 'SELECT income.id, income.name, income.recieved, income.due_on, income.recieved_on, ';
    SQLSTR := SQLSTR || 'income.recurring, income.amount_expected, income.amount_actual, income.users_id, ';
    SQLSTR := SQLSTR || 'income.category_id, income.accounts_id FROM income ';
    SQLSTR := SQLSTR || 'INNER JOIN income_categories ON income_categories.id = income.category_id ';
    SQLSTR := SQLSTR || 'INNER JOIN period ON period.id = income_categories.period_id ';
    SQLSTR := SQLSTR || 'INNER JOIN users ON users.id = period.users_id ';
 	SQLSTR := SQLSTR || 'WHERE users.id = :V_USRID ';
    SQLSTR := SQLSTR || CATSTR ;
    SQLSTR := SQLSTR || ACTSTR ;
    SQLSTR := SQLSTR || INCSTR ;
    SQLSTR := SQLSTR || RECSTR ;
    SQLSTR := SQLSTR || RCGSTR ;
    SQLSTR := SQLSTR || AEXSTR ;
    SQLSTR := SQLSTR || AACSTR ;
    SQLSTR := SQLSTR || PERSTR ;
    SQLSTR := SQLSTR || 'AND income.due_on >= :V_STDAT AND income.due_on < :V_ENDAT ';
	
	
	OPEN P_CURSOR
	FOR SQLSTR
	USING v_usrid, v_stdat, v_endat;
	
END;

--SQLSTR3 := '    AND TBL.COL= ''' || RTRIM(VAR) || '''  ';
--SQLSTR3 := '    AND TBL.COL= ''' || V_VAR || '''  ';