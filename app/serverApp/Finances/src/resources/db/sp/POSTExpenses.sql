create or replace NONEDITIONABLE PROCEDURE POSTEXPENSES
(
    name            IN VARCHAR2,
    paid            IN NUMBER,
    due_by          IN TIMESTAMP,  
    recurring		IN NUMBER,
    amount_due      IN NUMBER,
    amount_paid     IN NUMBER,
    users_id        IN INT,
    category_id     IN INT,
    
    cat_name        IN VARCHAR2,
    cat_due_by      IN TIMESTAMP,
    cat_paid_on     IN TIMESTAMP,
    cat_amount_paid IN NUMBER,
    cat_recurring   IN NUMBER,
    cat_amount_due  IN NUMBER,
    period          IN INT,
    new_cat_bool    IN INT,
    expenses_id     OUT INT

)
AS 
    --expenses_name variable
	V_EXNAM VARCHAR2 (40) DEFAULT '';
    
    --paid variable
    V_EXPAI NUMBER DEFAULT 0;
    
    --due_by variable
	V_DUEBY TIMESTAMP := TO_TIMESTAMP('9999-12-31-23.59.59.999999', 'YYYY-MM-DD-HH24.MI.SS.FF6');
    
    --recurring variable
    V_RCURG NUMBER DEFAULT 0;
    
    --amount_due variable
	V_AMDUE NUMBER (12,2) DEFAULT 0.00;
    
    --amount_paid variable
	V_AMPAI NUMBER (12,2) DEFAULT 0.00;
    
    --users_id variable
    V_USRID INT DEFAULT 1;
    
    --category_id variable
    V_CATID INT;
    
    --cat_name variable
	V_CATNM VARCHAR2 (40) DEFAULT '';
    
    --cat_due_by variable
	V_CATDB TIMESTAMP := TO_TIMESTAMP('9999-12-31-23.59.59.999999', 'YYYY-MM-DD-HH24.MI.SS.FF6');
    
    --cat_paid_on variable
	V_CATPO TIMESTAMP := TO_TIMESTAMP('9999-12-31-23.59.59.999999', 'YYYY-MM-DD-HH24.MI.SS.FF6');
    
    --cat_amount_paid variable
	V_CATAP NUMBER (12,2) DEFAULT 0.00;
    
    --cat_recurring variable
    V_CATRC NUMBER DEFAULT 1;
    
    --cat_amount_due variable
	V_CATAD NUMBER (12,2) DEFAULT 0.00;
    
    --period variable
	V_PERIO INT;
    
    --new_cat_bool variable
    V_NCATB INT;

BEGIN

    --expenses_name variable
	IF (RTRIM(name)IS NOT NULL)THEN
		V_EXNAM := name;
	END IF;
    
    --paid variable
	IF(RTRIM(paid)IS NOT NULL)THEN  
		V_EXPAI := paid;
	END IF;
    
    --due_by variable
	IF (RTRIM(due_by)IS NOT NULL)THEN
		V_DUEBY := due_by;
	END IF;
    
    --recurring variable
	IF(RTRIM(recurring)IS NOT NULL)THEN  
		V_RCURG := recurring;
	END IF;
    
    --amount_due variable
    IF(RTRIM(amount_due)IS NOT NULL)THEN
		V_AMDUE := amount_due;
	END IF;
    
    --amount_paid variable
    IF(RTRIM(amount_paid)IS NOT NULL)THEN
		V_AMPAI := amount_paid;
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
		V_CATDB := cat_due_by;
	END IF;
    
    --cat_paid_on variable
	IF (RTRIM(cat_paid_on)IS NOT NULL)THEN
		V_CATPO := cat_paid_on;
	END IF;
    
    --ccat_amount_paid variable
    IF(RTRIM(cat_amount_paid)IS NOT NULL)THEN
		V_CATAP := cat_amount_paid;
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
            INSERT INTO expenses_categories (name, due_by, paid_on, amount_paid, recurring, amount_due, users_id, period_id) 
                    VALUES ( V_CATNM, V_CATDB, V_CATPO, V_CATAP, V_CATRC, V_CATAD, V_USRID, V_PERIO )
                    RETURNING id into V_CATID;
        END;
	END IF;
    
    
    INSERT INTO expenses (name, paid, due_by, recurring, amount_due, amount_paid, users_id, category_id) 
        VALUES ( V_EXNAM, V_EXPAI, V_DUEBY, V_RCURG, V_AMDUE, V_AMPAI, V_USRID, V_CATID )
    RETURNING id into expenses_id;
    
END POSTEXPENSES;