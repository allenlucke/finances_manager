create or replace NONEDITIONABLE PROCEDURE POSTINCOME  
(
    name            IN VARCHAR2,
    due_on          IN TIMESTAMP,
    recurring       IN NUMBER,
    amount_expected IN NUMBER,   
    users_id		IN INT,
    category_id 	IN INT,  
--    accounts_id     IN INT,
--    recieved        IN NUMBER,
--    amount_actual   IN NUMBER,
    cat_name        IN VARCHAR2,
    cat_due_by      IN TIMESTAMP,
    cat_recurring   IN NUMBER,
    cat_amount_due  IN NUMBER,
    period          IN INT,
    new_cat_bool    IN INT,
    income_id       OUT INT

)
AS
    --income_name variable
	V_INNAM VARCHAR2 (40) DEFAULT '';
    
    --due_on variable
	V_DUEON TIMESTAMP := TO_TIMESTAMP('9999-12-31-23.59.59.999999', 'YYYY-MM-DD-HH24.MI.SS.FF6');
    
    --recurring variable
    V_RCURG NUMBER DEFAULT 0;
    
    --amount_expected variable
	V_AMEXP NUMBER (12,2) DEFAULT 0.00;
    
    --users_id variable
    V_USRID INT DEFAULT 1;

    --category_id variable
    V_CATID INT;

    --cat_name variable
	V_CATNM VARCHAR2 (40) DEFAULT '';

    --cat_due_by variable
	V_CATDU TIMESTAMP := TO_TIMESTAMP('9999-12-31-23.59.59.999999', 'YYYY-MM-DD-HH24.MI.SS.FF6');
    
    --cat_recurring variable
    V_CATRC NUMBER DEFAULT 1;
    
    --cat_amount_due variable
	V_CATAD NUMBER (12,2) DEFAULT 0.00;

    --period variable
	V_PERIO INT;

    --new_cat_bool variable
    V_NCATB INT;
    
    --accounts_id  variable
--    V_ACTID INT;
    
    --recieved variable
--	V_RECVD NUMBER DEFAULT 0;
   
    --amount_actual variable
-- 	V_AMACT NUMBER (12,2) DEFAULT 0.00;
    
    
    
BEGIN

    --income_name variable
	IF (RTRIM(name)IS NOT NULL)THEN
		V_INNAM := name;
	END IF;
    
    --due_on variable
	IF (RTRIM(due_on)IS NOT NULL)THEN
		V_DUEON := due_on;
	END IF;
    
    --recurring variable
	IF(RTRIM(recurring)IS NOT NULL)THEN  
		V_RCURG := recurring;
	END IF;
    
    --amount_expected variable
    IF(RTRIM(amount_expected)IS NOT NULL)THEN
		V_AMEXP := amount_expected;
	END IF;
    
    --users_id variable assignment
    IF(RTRIM(users_id)IS NOT NULL)THEN
		V_USRID := users_id;
	END IF; 

    --category_id variable
	IF (RTRIM(category_id)IS NOT NULL)THEN
		V_CATID := category_id;
	END IF;
    
    --cat_name variable variable
	IF (RTRIM(cat_name)IS NOT NULL)THEN
		V_CATNM := cat_name;
	END IF;
    
    --cat_due_by variable
	IF (RTRIM(cat_due_by)IS NOT NULL)THEN
		V_CATDU := cat_due_by;
	END IF;
    
    --cat_recurring variable
	IF(RTRIM(cat_recurring)IS NOT NULL)THEN  
		V_CATRC := cat_recurring;
	END IF;
    
    --cat_amount_due variable
    IF(RTRIM(cat_amount_due)IS NOT NULL)THEN
		V_CATAD := cat_amount_due;
	END IF;
    
    --period variable
	IF (RTRIM(period)IS NOT NULL)THEN
		V_PERIO := period;
	END IF;

    --new_cat_bool variable/querystring
    IF (RTRIM(new_cat_bool)IS NOT NULL)THEN
		V_NCATB := new_cat_bool;
	END IF;
    IF (RTRIM(new_cat_bool)=1)THEN
        BEGIN
            INSERT INTO income_categories (name, due_by, recurring, amount_due, users_id, period_id) 
                    VALUES ( V_CATNM, V_CATDU, V_CATRC, V_CATAD, V_USRID, V_PERIO )
                    RETURNING id into V_CATID;
        END;
	END IF;


    INSERT INTO income (name, due_on, recurring, amount_expected, users_id, category_id) 
        VALUES ( V_INNAM, V_DUEON, V_RCURG, V_AMEXP, V_USRID, V_CATID )
    RETURNING id into income_id;


--    INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
--        VALUES('aprtest1', '20-APR-2021', 1, 80.60, 1, 3 );


END;