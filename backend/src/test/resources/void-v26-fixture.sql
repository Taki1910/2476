-- Schema-upgrade evidence only; not a simulated business workflow.
DECLARE @account UNIQUEIDENTIFIER=NEWID(), @product UNIQUEIDENTIFIER=NEWID(),
        @variant UNIQUEIDENTIFIER=NEWID(), @branch UNIQUEIDENTIFIER=NEWID(),
        @location UNIQUEIDENTIFIER=NEWID(), @price UNIQUEIDENTIFIER=NEWID();
INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at)
VALUES(@account,'v27-fixture@example.test','fixture','ENABLED',1,0,SYSUTCDATETIME(),SYSUTCDATETIME());
INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES(@product,'Migration fixture',0,SYSUTCDATETIME());
INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at)
SELECT @variant,id,'V27-FIXTURE','40','Black','PUBLISHED',0,SYSUTCDATETIME() FROM catalog_product WHERE public_id=@product;
INSERT INTO pricing_variant_price(public_id,variant_id,amount,entity_version,valid_from,updated_at)
SELECT @price,id,100000,0,SYSUTCDATETIME(),SYSUTCDATETIME() FROM catalog_product_variant WHERE public_id=@variant;
INSERT INTO org_branch(public_id,code,name,enabled,created_at) VALUES(@branch,'V27','Migration branch',1,SYSUTCDATETIME());
INSERT INTO org_location(public_id,branch_id,code,name,enabled,created_at)
SELECT @location,id,'V27-FLOOR','Migration floor',1,SYSUTCDATETIME() FROM org_branch WHERE public_id=@branch;
DECLARE @n INT=1;
WHILE @n<=5 BEGIN
    DECLARE @reservation UNIQUEIDENTIFIER=NEWID(),@order UNIQUEIDENTIFIER=NEWID(),@item UNIQUEIDENTIFIER=NEWID();
    DECLARE @attemptState VARCHAR(24)=CASE @n WHEN 1 THEN 'CREATED' WHEN 2 THEN 'UNKNOWN' WHEN 3 THEN 'SUCCEEDED' WHEN 4 THEN 'DEFINITIVE_FAILED' ELSE 'REVIEW_REQUIRED' END;
    DECLARE @allocationState VARCHAR(16)=CASE @n WHEN 3 THEN 'SUCCEEDED' WHEN 4 THEN 'RELEASED' ELSE 'ACTIVE' END;
    INSERT INTO inventory_reservation(public_id,owner_account_public_id,variant_id,location_id,quantity,status,entity_version,created_at,adopted_at,expires_at)
    SELECT @reservation,@account,v.id,l.id,1,'ADOPTED',0,DATEADD(minute,-5,SYSUTCDATETIME()),DATEADD(minute,-4,SYSUTCDATETIME()),DATEADD(hour,1,SYSUTCDATETIME())
    FROM catalog_product_variant v CROSS JOIN org_location l WHERE v.public_id=@variant AND l.public_id=@location;
    INSERT INTO commerce_order(public_id,owner_account_public_id,responsible_branch_public_id,reservation_public_id,currency,status,entity_version,created_at,channel,merchandise_amount,shipping_fee_amount,total_amount)
    VALUES(@order,@account,@branch,@reservation,'VND','PENDING_PAYMENT',0,SYSUTCDATETIME(),'ONLINE',100000,0,100000);
    INSERT INTO commerce_order_item(public_id,order_id,variant_public_id,location_public_id,quantity,unit_price_amount,sku_snapshot,size_snapshot,reservation_public_id,price_version_public_id,color_snapshot)
    SELECT @item,id,@variant,@location,1,100000,'V27-FIXTURE','40',@reservation,@price,'Black' FROM commerce_order WHERE public_id=@order;
    INSERT INTO payment(public_id,order_id,currency,created_at) SELECT NEWID(),id,'VND',SYSUTCDATETIME() FROM commerce_order WHERE public_id=@order;
    DECLARE @payment BIGINT=SCOPE_IDENTITY();
    INSERT INTO payment_void_operation(public_id,payment_id,order_public_id,actor_account_public_id,idempotency_key,requested_amount,currency,status,created_at)
    VALUES(NEWID(),@payment,@order,@account,CONCAT('fixture-',@n),100000,'VND','PROCESSING',SYSUTCDATETIME());
    DECLARE @operation BIGINT=SCOPE_IDENTITY();
    INSERT INTO payment_void_attempt(public_id,void_operation_id,generation,actor_account_public_id,idempotency_key,merchant_request_reference,amount,status,created_at,resolved_at)
    VALUES(NEWID(),@operation,1,@account,CONCAT('fixture-',@n),REPLACE(CONVERT(VARCHAR(36),NEWID()),'-',''),100000,@attemptState,SYSUTCDATETIME(),CASE WHEN @n=1 THEN NULL ELSE SYSUTCDATETIME() END);
    DECLARE @attempt BIGINT=SCOPE_IDENTITY();
    INSERT INTO payment_void_allocation(public_id,void_operation_id,void_attempt_id,component_type,component_public_id,amount,status,created_at,resolved_at)
    VALUES(NEWID(),@operation,@attempt,'ORDER_ITEM',@item,100000,@allocationState,SYSUTCDATETIME(),CASE WHEN @n IN (3,4) THEN SYSUTCDATETIME() ELSE NULL END);
    SET @n=@n+1;
END;
