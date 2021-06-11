create or replace NONEDITIONABLE PROCEDURE GETExpenses
(
	users_id		IN INT,
    category_id 	IN INT,
    accounts_id     IN INT,
    name            IN VARCHAR2,
    paid	        IN NUMBER,
    recurring       IN NUMBER,
    amount_due		IN NUMBER,
    amount_paid		IN NUMBER,
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

    --expenses_name variable/querystring declaration
	V_EXNAM VARCHAR2 (40) DEFAULT '';
    EXPSTR  VARCHAR2 (100) DEFAULT '';
    
    --paid variable/querystring declaration
	V_PAID_ NUMBER DEFAULT 0;
    PAISTR  VARCHAR2 (50) DEFAULT '';
    
    --recurring variable/querystring declaration
    V_RCURG NUMBER DEFAULT 1;
    RCGSTR  VARCHAR2 (50) DEFAULT '';
    
    --amount_due variable/querystring declaration
	V_AMDUE NUMBER (12,2) DEFAULT 0.00;
    ADUSTR  VARCHAR2 (80) DEFAULT '';
    
    --amount_paid variable/querystring declaration
	V_AMPAI NUMBER (12,2) DEFAULT 0.00;
    APASTR  VARCHAR2 (80) DEFAULT '';
    
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
        CATSTR := ' AND expenses.category_id = ''' || V_CATID || ''' ';
	END IF;

    --accounts_id variable/querystring assignment
	IF (RTRIM(accounts_id)IS NOT NULL)THEN
		V_ACTID := accounts_id;
	END IF;
    IF (RTRIM(accounts_id)IS NOT NULL)THEN
        ACTSTR := ' AND expenses.accounts_id = ''' || V_ACTID || ''' ';
	END IF;

    --expenses_name variable/querystring assignment
	IF (RTRIM(name)IS NOT NULL)THEN
		V_EXNAM := name;
	END IF;
    IF (RTRIM(name)IS NOT NULL)THEN
        EXPSTR := ' AND expenses.name = ''' || V_EXNAM || ''' ';
	END IF;
    
    --paid variable/querystring assignment
	IF(RTRIM(paid)IS NOT NULL)THEN
		V_PAID_ := paid;
	END IF;
    IF (RTRIM(paid)IS NOT NULL AND RTRIM(paid) != 2 )THEN
        PAISTR := ' AND expenses.paid = ''' || V_PAID_ || ''' ';
	END IF;
    
    --recurring variable/querystring assignment
	IF(RTRIM(recurring)IS NOT NULL)THEN  
		V_RCURG := recurring;
	END IF;
    IF (RTRIM(recurring)IS NOT NULL AND RTRIM(recurring) != 2 )THEN
        RCGSTR := ' AND expenses.recurring = ''' || V_RCURG || ''' ';
	END IF;
    
    --amount_due variable/querystring assignment
    IF(RTRIM(amount_due)IS NOT NULL)THEN
		V_AMDUE := amount_due;
	END IF;
    IF (RTRIM(amount_due)IS NOT NULL)THEN
        ADUSTR := ' AND expenses.amount_due = ''' || V_AMDUE || ''' ';
	END IF;
    
    --amount_paid variable/querystring assignment
    IF(RTRIM(amount_paid)IS NOT NULL)THEN
		V_AMPAI := amount_paid;
	END IF;
    IF (RTRIM(amount_paid)IS NOT NULL)THEN
        APASTR := ' AND expenses.amount_paid = ''' || V_AMPAI || ''' ';
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

	
	SQLSTR := SQLSTR || 'SELECT expenses.id, expenses.name, expenses.paid, expenses.due_by, expenses.paid_on, ';
    SQLSTR := SQLSTR || 'expenses.recurring, expenses.amount_due, expenses.amount_paid, expenses.users_id, ';
    SQLSTR := SQLSTR || 'expenses.category_id, expenses_categories.name AS category_name, expenses.accounts_id FROM expenses ';
    SQLSTR := SQLSTR || 'INNER JOIN expenses_categories ON expenses_categories.id = expenses.category_id ';
    SQLSTR := SQLSTR || 'INNER JOIN period ON period.id = expenses_categories.period_id ';
    SQLSTR := SQLSTR || 'INNER JOIN users ON users.id = period.users_id ';
 	SQLSTR := SQLSTR || 'WHERE users.id = :V_USRID ';
    SQLSTR := SQLSTR || CATSTR ;
    SQLSTR := SQLSTR || ACTSTR ;
    SQLSTR := SQLSTR || EXPSTR ;
    SQLSTR := SQLSTR || PAISTR ;
    SQLSTR := SQLSTR || RCGSTR ;
    SQLSTR := SQLSTR || ADUSTR ;
    SQLSTR := SQLSTR || APASTR ;
    SQLSTR := SQLSTR || PERSTR ;
    SQLSTR := SQLSTR || 'AND expenses.due_by >= :V_STDAT AND expenses.due_by < :V_ENDAT ';
	
	
	OPEN P_CURSOR
	FOR SQLSTR
	USING v_usrid, v_stdat, v_endat;
	
END;