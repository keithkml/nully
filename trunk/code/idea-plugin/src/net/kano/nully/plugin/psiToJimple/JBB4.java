package net.kano.nully.plugin.psiToJimple;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.tree.IElementType;
import soot.FastHierarchy;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.jimple.Jimple;

import java.util.ArrayList;
import java.util.List;

public abstract class JBB4 extends JBB3 {
    /**
     * Expression Creation
     */
    protected soot.Value createExpr(PsiExpression expr){
        //System.out.println("create expr: "+expr+" type: "+expr.getClass());
        // maybe right here check if expr has constant val and return that
        // instead
        PsiConstantEvaluationHelper constHelper = expr.getManager()
                .getConstantEvaluationHelper();
        PsiType type = expr.getType();
        Object constValue = constHelper.computeConstantExpression(expr);
        if (constValue != null
                && type != null && !(expr instanceof PsiBinaryExpression
                && type.equalsToText("java.lang.String")) ){
            return createConstant(expr, constValue);
        }
        if (expr instanceof PsiAssignmentExpression) {
            return getAssignLocal((PsiAssignmentExpression)expr);
        }
        else if (expr instanceof PsiLiteralExpression) {
            return createLiteral((PsiLiteralExpression)expr);
        }
        else if (expr instanceof PsiClassObjectAccessExpression) {
            return getSpecialClassLitLocal((PsiClassObjectAccessExpression) expr);
        }
        else if (expr instanceof PsiReferenceExpression) {
            PsiReferenceExpression rexpr = (PsiReferenceExpression) expr;
            PsiElement resolved = rexpr.resolve();
            if (resolved instanceof PsiField) {
                return getFieldLocal(rexpr);
            } else if (resolved instanceof PsiParameter
                    || resolved instanceof PsiLocalVariable) {
                return getLocal(rexpr);
            }
        }
        else if (expr instanceof PsiBinaryExpression) {
            return getBinaryLocal((PsiBinaryExpression)expr);
        }
        else if (expr instanceof PsiConditionalExpression) {
            return getConditionalLocal((PsiConditionalExpression)expr);
        }
        else if (expr instanceof PsiPostfixExpression
                || expr instanceof PsiPrefixExpression) {
            return getUnaryLocal(expr);
        }
        else if (expr instanceof PsiTypeCastExpression) {
            return getCastLocal((PsiTypeCastExpression)expr);
        }
        else if (expr instanceof PsiArrayAccessExpression) {
            return getArrayRefLocal((PsiArrayAccessExpression)expr);
        }
        else if (expr instanceof PsiNewExpression) {
            PsiNewExpression nexpr = (PsiNewExpression) expr;
            if (nexpr.getArrayDimensions().length == 0) {
                return getNewLocal(nexpr);
            } else {
                return getNewArrayLocal(nexpr);
            }
        }
        else if (expr instanceof PsiMethodCallExpression) {
            return getCallLocal((PsiMethodCallExpression)expr);
        }
        else if (expr instanceof PsiThisExpression) {
            return getThisLocal((PsiThisExpression) expr);

        } else if (expr instanceof PsiSuperExpression) {
            return getSuperLocal((PsiSuperExpression) expr);
        }
        else if (expr instanceof PsiInstanceOfExpression) {
            return getInstanceOfLocal((PsiInstanceOfExpression)expr);
        }
        throw new IllegalArgumentException("Unhandled Expression: "+expr);

    }

    private soot.Value getStrConAssignLocal(PsiAssignmentExpression assign){
        soot.jimple.AssignStmt stmt;
        soot.Value left = base().createLHS(assign.getLExpression());

        soot.Value right = getStringConcatAssignRightLocal(assign);
        stmt = soot.jimple.Jimple.v().newAssignStmt(left, right);
        body.getUnits().add(stmt);
        Util.addPsiTags(stmt, assign);
        Util.addPsiTags(stmt.getRightOpBox(), assign.getRExpression());
        Util.addPsiTags(stmt.getLeftOpBox(), assign.getLExpression());
        if (left instanceof soot.Local){
            return left;
        }
        else {
            return right;
        }

    }

    /**
     * Assign Expression Creation
     */
    protected soot.Value getAssignLocal(PsiAssignmentExpression assign) {

        // handle private access field assigns
        //HashMap accessMap = ((PolyglotMethodSource)body.getMethod().getSource()).getPrivateAccessMap();
        // if assigning to a field and the field is private and its not in
        // this class (then it had better be in some outer class and will
        // be handled as such)
        PsiReferenceExpression lvalue = (PsiReferenceExpression) assign.getLExpression();
        if (base().needsAccessor(lvalue)){
            //if ((assign.left() instanceof polyglot.ast.Field) && (needsPrivateAccessor((polyglot.ast.Field)assign.left()) || needsProtectedAccessor((polyglot.ast.Field)assign.left()))){
            //((polyglot.ast.Field)assign.left()).fieldInstance().flags().isPrivate() && !Util.getSootType(((polyglot.ast.Field)assign.left()).fieldInstance().container()).equals(body.getMethod().getDeclaringClass().getType())){
            return base().handlePrivateFieldAssignSet(assign);
        }

        IElementType op = assign.getOperationSign().getTokenType();
        if (op == JavaTokenType.EQ){
            return getSimpleAssignLocal(assign);
        }

        PsiExpression rvalue = assign.getRExpression();
        if ((op == JavaTokenType.PLUSEQ)
                && rvalue.getType().equalsToText("java.lang.String")){
            return getStrConAssignLocal(assign);
        }

        soot.Value left = base().createLHS(lvalue);
        soot.Value left2 = (soot.Value)left.clone();

        soot.Local leftLocal;
        if (left instanceof soot.Local){
            leftLocal = (soot.Local)left;

        }
        else {
            leftLocal = lg.generateLocal(left.getType());
            soot.jimple.AssignStmt stmt1 = soot.jimple.Jimple.v().newAssignStmt(leftLocal, left);
            body.getUnits().add(stmt1);
            Util.addPsiTags(stmt1, assign);
        }


        soot.Value right = base().getAssignRightLocal(assign, leftLocal);
        soot.jimple.AssignStmt stmt2 = soot.jimple.Jimple.v().newAssignStmt(leftLocal, right);
        body.getUnits().add(stmt2);
        Util.addPsiTags(stmt2, assign);
        Util.addPsiTags(stmt2.getRightOpBox(), rvalue);
        Util.addPsiTags(stmt2.getLeftOpBox(), lvalue);

        if (!(left instanceof soot.Local)) {
            soot.jimple.AssignStmt stmt3 = soot.jimple.Jimple.v().newAssignStmt(left2, leftLocal);
            body.getUnits().add(stmt3);
            Util.addPsiTags(stmt3, assign);
            Util.addPsiTags(stmt3.getRightOpBox(), rvalue);
            Util.addPsiTags(stmt3.getLeftOpBox(), lvalue);
        }

        return leftLocal;

    }

    /**
     * Field Expression Creation
     */
    private soot.Value getFieldLocal(PsiReferenceExpression ref){
        PsiExpression receiver = ref.getQualifierExpression();

        PsiField field = (PsiField) ref.resolve();
        String refName = ref.getReferenceName();
        if (refName.equals("length")
                && receiver != null && receiver.getType() instanceof PsiArrayType) {
            return getSpecialArrayLengthLocal(ref);
        }
        else if (refName.equals("class")){
            throw new IllegalArgumentException("Should go through ClassLit: "
                    + ref);
        }
        else if (base().needsAccessor(ref)){
            soot.Value base;
            if (receiver == null || receiver.getType() == null) {
                base = null;
            } else {
                base = base().getBaseLocal(receiver);
            }
            return getPrivateAccessFieldLocal(field, base);
        }
        if ((receiver instanceof PsiSuperExpression)
                && (((PsiSuperExpression)receiver).getQualifier() != null)){
            return getSpecialSuperQualifierLocalForField(ref);
        }
        else if (shouldReturnConstant(ref)){
            return getReturnConstant(ref);
        }
        else {
            soot.jimple.FieldRef fieldRef = getFieldRef(ref);
            soot.Local baseLocal = generateLocal(field.getType());
            soot.jimple.AssignStmt fieldAssignStmt = soot.jimple.Jimple.v()
                    .newAssignStmt(baseLocal, fieldRef);

            body.getUnits().add(fieldAssignStmt);
            Util.addPsiTags(fieldAssignStmt, ref);
            Util.addPsiTags(fieldAssignStmt.getRightOpBox(), ref);
            return baseLocal;
        }
    }

    protected boolean needsAccessor(PsiExpression expr){
        if (expr instanceof PsiReferenceExpression) {
            PsiElement referenced = ((PsiReferenceExpression) expr).resolve();
            if (referenced instanceof PsiField) {
                return needsAccessor((PsiField) referenced);
            }
        } else if (expr instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) expr;
            return needsAccessor(call.resolveMethod());
        }

        return false;
    }

    /**
     * needs accessors:
     * when field or meth is private and in some other class
     * when field or meth is protected and in
     */
    protected boolean needsAccessor(PsiMember inst){
        SootMethod method = body.getMethod();
        Type clsType = Util.getSootType(inst.getContainingClass());
        if (inst.hasModifierProperty("private")) {
            if (!clsType.equals(method.getDeclaringClass().getType())){
                return true;
            }
        }
        else if (inst.hasModifierProperty("protected")){
            if (clsType.equals(method.getDeclaringClass().getType())){
                return false;
            }
            soot.SootClass currentClass = method.getDeclaringClass();
            if (currentClass.getSuperclass().getType().equals(clsType)){
                return false;
            }
            while (currentClass.hasOuterClass()){
                currentClass = currentClass.getOuterClass();
                if (clsType.equals(currentClass.getType())){
                    return false;
                }
                else if (clsType.equals(currentClass.getSuperclass().getType())){
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    /**
     * needs a protected access method if field is protected and in
     * a super class of the outer class of the innerclass trying to access
     * the field (ie not in self or in outer of self)
     */
/*protected boolean needsProtectedAccessor(polyglot.ast.Field field){
        //return false;
        if (field.fieldInstance().flags().isProtected()){
            if (Util.getSootType(field.fieldInstance().container()).equals(body.getMethod().getDeclaringClass().getType())){
                return false;
            }
            soot.SootClass currentClass = body.getMethod().getDeclaringClass();
            while (currentClass.hasOuterClass()){
                currentClass = currentClass.getOuterClass();
                if (Util.getSootType(field.fieldInstance().container()).equals(currentClass.getType())){
                    return false;
                }
                else if (Util.getSootType(field.fieldInstance().container()).equals(currentClass.getSuperclass().getType())){
                    return true;
                }
            }
            return false;
        }
        return false;
        /*
        if (field.fieldInstance().flags().isProtected()){
            if (!Util.getSootType(field.fieldInstance().container()).equals(body.getMethod().getDeclaringClass().getType())){
                soot.SootClass checkClass = body.getMethod().getDeclaringClass();
                while (checkClass.hasOuterClass()){
                    checkClass = checkClass.getOuterClass();
                    if (Util.getSootType(field.fieldInstance().container()).equals(checkClass.getType())){
                        return false;
                    }

                }
                return true;
            }
        }
        return false;*/
//}


    private soot.jimple.Constant getReturnConstant(PsiReferenceExpression ref){
        PsiConstantEvaluationHelper constHelper = ref.getManager()
                .getConstantEvaluationHelper();
        PsiField field = ((PsiField) ref.resolve());
        Object constValue = constHelper.computeConstantExpression(field.getInitializer());
        return getConstant(constValue, field.getType());
    }

    private boolean shouldReturnConstant(PsiReferenceExpression ref){
        PsiField field = (PsiField) ref.resolve();
        PsiExpression initializer = field.getInitializer();
        if (field.getModifierList().hasModifierProperty("final")
                && initializer != null) {
            PsiConstantEvaluationHelper constHelper = ref.getManager()
                    .getConstantEvaluationHelper();
            if (constHelper.computeConstantExpression(initializer) != null) {
                return true;
            }
        }
        return false;
    }

    protected abstract soot.jimple.FieldRef getFieldRef(PsiReferenceExpression field);

    /**
     * For Inner Classes - to access private fields of their outer class
     */
    protected soot.Local getPrivateAccessFieldLocal(PsiField field, soot.Value base) {

        // need to add access method
        // if private add to the containing class
        // but if its protected then add to outer class - not containing
        // class because in this case the containing class is the super class

        soot.SootMethod toInvoke;
        soot.SootClass invokeClass;
        SootClass parentClass = ((soot.RefType) Util.getSootType(
                field.getType())).getSootClass();
        PsiModifierList fieldMods = field.getModifierList();
        if (fieldMods.hasModifierProperty("private")){
            toInvoke = addGetFieldAccessMeth(parentClass, field);
            invokeClass = parentClass;
        }
        else {
            if (initialResolver.hierarchy() == null){
                initialResolver.hierarchy(new soot.FastHierarchy());
            }
            soot.SootClass containingClass = parentClass;
            soot.SootClass addToClass;
            if (body.getMethod().getDeclaringClass().hasOuterClass()){
                addToClass = body.getMethod().getDeclaringClass().getOuterClass();

                while (!initialResolver.hierarchy().canStoreType(
                        containingClass.getType(), addToClass.getType())){
                    if (addToClass.hasOuterClass()){
                        addToClass = addToClass.getOuterClass();
                    }
                    else {
                        break;
                    }
                }
            }
            else{
                addToClass = containingClass;
            }
            invokeClass = addToClass;
            toInvoke = addGetFieldAccessMeth(addToClass, field);
        }

        List<Value> params = new ArrayList<Value>();
        if (fieldMods.hasModifierProperty("static")) {
            assert base == null;
        } else {
            assert base != null;
            params.add(base);
        }

        return Util.getPrivateAccessFieldInvoke(toInvoke.makeRef(), params, body, lg);
    }

    /**
     *  Array Length local for example a.length w/o brackets gets length
     *  of array
     */
    protected soot.Local getSpecialArrayLengthLocal(PsiReferenceExpression ref) {

        soot.Local localField;
        PsiExpression receiver = ref.getQualifierExpression();
        if (Util.isLocalReference(receiver)) {
            localField = getLocal((PsiReferenceExpression)receiver);
        }
        else {
            localField = (soot.Local)base().createExpr(receiver);
        }
        soot.jimple.LengthExpr lengthExpr = soot.jimple.Jimple.v().newLengthExpr(localField);
        soot.Local retLocal = lg.generateLocal(soot.IntType.v());
        soot.jimple.Stmt assign = soot.jimple.Jimple.v().newAssignStmt(retLocal, lengthExpr);
        body.getUnits().add(assign);
        Util.addPsiTags(assign, ref);
        Util.addPsiTags(lengthExpr.getOpBox(), receiver);
        return retLocal;
    }

    /**
     * Binary Expression Creation
     */
    private Value getBinaryLocal(PsiBinaryExpression binary) {

        Value rhs;
        PsiJavaToken sign = binary.getOperationSign();
        IElementType op = sign.getTokenType();
        if (op == JavaTokenType.ANDAND) {
            return createCondAnd(binary);
        }
        if (op == JavaTokenType.OROR) {
            return createCondOr(binary);
        }

        if (binary.getType().equalsToText("java.lang.String")){
            //System.out.println("binary: "+binary);
            if (areAllStringLits(binary)){
                String result = createStringConstant(binary);
                //System.out.println("created string constant: "+result);
                return soot.jimple.StringConstant.v(result);
            }
            else {
                soot.Local sb = (soot.Local)createStringBuffer(binary);
                generateAppends(binary.getLOperand(), sb);
                generateAppends(binary.getROperand(), sb);
                return createToString(sb, binary);
            }
        }

        Value lVal = base().createExpr(binary.getLOperand());
        Value rVal = base().createExpr(binary.getROperand());

        if (isComparisonBinary(sign)) {
            rhs = getBinaryComparisonExpr(lVal, rVal, sign);
        }
        else {
            rhs = getBinaryExpr(lVal, rVal, sign);
        }

        if (rhs instanceof soot.jimple.BinopExpr) {
            Util.addPsiTags(((soot.jimple.BinopExpr)rhs).getOp1Box(),
                    binary.getLOperand());
            Util.addPsiTags(((soot.jimple.BinopExpr)rhs).getOp2Box(),
                    binary.getROperand());
        }

        if (rhs instanceof soot.jimple.ConditionExpr) {
            return rhs;
        }

        soot.Local lhs = generateLocal(binary.getType());


        soot.jimple.AssignStmt assignStmt = soot.jimple.Jimple.v().newAssignStmt(lhs, rhs);
        body.getUnits().add(assignStmt);


        Util.addPsiTags(assignStmt.getRightOpBox(), binary);
        return lhs;
    }

    private boolean areAllStringLits(PsiExpression node){
        PsiConstantEvaluationHelper constHelper = node.getManager()
                .getConstantEvaluationHelper();
        //System.out.println("node in is string lit: "+node+" kind: "+node.getClass());
        if (node instanceof PsiLiteralExpression && node.getType().equalsToText("java.lang.String")) {
            return true;

        } else if (Util.isFieldReference(node)) {
            if (shouldReturnConstant((PsiReferenceExpression) node)) {
                return true;
            } else {
                return false;
            }
        } else if (node instanceof PsiBinaryExpression) {
            if (areAllStringLitsBinary((PsiBinaryExpression) node)) return true;
            return false;
        } else if (node instanceof PsiTypeCastExpression
                || node instanceof PsiLiteralExpression) {
            if (constHelper.computeConstantExpression(node) != null) {
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean areAllStringLitsBinary(PsiBinaryExpression binary){
        return areAllStringLits(binary.getLOperand())
                && areAllStringLits(binary.getROperand());
    }

    private String createStringConstant(PsiBinaryExpression node){
        return Util.evaluateConstantValue(node).toString();
    }

    private boolean isComparisonBinary(PsiJavaToken op) {
        IElementType type = op.getTokenType();
        return (type == JavaTokenType.EQEQ) || (type == JavaTokenType.NE) ||
                (type == JavaTokenType.GE) || (type == JavaTokenType.GT) ||
                (type == JavaTokenType.LE) || (type == JavaTokenType.LT);
    }

    /**
     * Creates a binary expression that is not a comparison
     */
    private Value getBinaryExpr(Value lVal, Value rVal, PsiJavaToken operator){

        Value rValue = null;

        if (lVal instanceof soot.jimple.ConditionExpr) {
            lVal = handleCondBinExpr((soot.jimple.ConditionExpr)lVal);
        }
        if (rVal instanceof soot.jimple.ConditionExpr) {
            rVal = handleCondBinExpr((soot.jimple.ConditionExpr)rVal);
        }
        IElementType op = operator.getTokenType();
        if (op == JavaTokenType.PLUS){

            rValue = soot.jimple.Jimple.v().newAddExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.MINUS){
            rValue = soot.jimple.Jimple.v().newSubExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.ASTERISK){
            rValue = soot.jimple.Jimple.v().newMulExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.DIV){
            rValue = soot.jimple.Jimple.v().newDivExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.GTGT){
            if (rVal.getType().equals(soot.LongType.v())){
                soot.Local intVal = lg.generateLocal(soot.IntType.v());
                soot.jimple.CastExpr castExpr = soot.jimple.Jimple.v()
                        .newCastExpr(rVal, soot.IntType.v());
                soot.jimple.AssignStmt assignStmt = soot.jimple.Jimple.v()
                        .newAssignStmt(intVal, castExpr);
                body.getUnits().add(assignStmt);
                rValue = soot.jimple.Jimple.v().newShrExpr(lVal, intVal);
            }
            else {
                rValue = soot.jimple.Jimple.v().newShrExpr(lVal, rVal);
            }
        }
        else if (op == JavaTokenType.GTGTGT){
            if (rVal.getType().equals(soot.LongType.v())){
                soot.Local intVal = lg.generateLocal(soot.IntType.v());
                soot.jimple.CastExpr castExpr = soot.jimple.Jimple.v()
                        .newCastExpr(rVal, soot.IntType.v());
                soot.jimple.AssignStmt assignStmt = soot.jimple.Jimple.v()
                        .newAssignStmt(intVal, castExpr);
                body.getUnits().add(assignStmt);
                rValue = soot.jimple.Jimple.v().newUshrExpr(lVal, intVal);
            }
            else {
                rValue = soot.jimple.Jimple.v().newUshrExpr(lVal, rVal);
            }
        }
        else if (op == JavaTokenType.LTLT){
            if (rVal.getType().equals(soot.LongType.v())){
                soot.Local intVal = lg.generateLocal(soot.IntType.v());
                soot.jimple.CastExpr castExpr = soot.jimple.Jimple.v().newCastExpr(
                        rVal, soot.IntType.v());
                soot.jimple.AssignStmt assignStmt = soot.jimple.Jimple.v().newAssignStmt(
                        intVal, castExpr);
                body.getUnits().add(assignStmt);
                rValue = soot.jimple.Jimple.v().newShlExpr(lVal, intVal);
            }
            else {
                rValue = soot.jimple.Jimple.v().newShlExpr(lVal, rVal);
            }
        }
        else if (op == JavaTokenType.AND){
            rValue = soot.jimple.Jimple.v().newAndExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.OR){
            rValue = soot.jimple.Jimple.v().newOrExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.XOR){
            rValue = soot.jimple.Jimple.v().newXorExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.PERC){
            rValue = soot.jimple.Jimple.v().newRemExpr(lVal, rVal);
        }
        else {
            throw new IllegalArgumentException("Binary not yet handled: " + op);
        }

        return rValue;
    }

    /**
     * Creates a binary expr that is a comparison
     */
    private Value getBinaryComparisonExpr(Value lVal, Value rVal,
            PsiJavaToken operator) {

        Value rValue;

        Jimple jimple = Jimple.v();
        IElementType op = operator.getTokenType();
        if (op == JavaTokenType.EQEQ){
            rValue = jimple.newEqExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.GE){
            rValue = jimple.newGeExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.GT){
            rValue = jimple.newGtExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.LE){
            rValue = jimple.newLeExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.LT){
            rValue = jimple.newLtExpr(lVal, rVal);
        }
        else if (op == JavaTokenType.NE){
            rValue = jimple.newNeExpr(lVal, rVal);
        }
        else {
            throw new IllegalArgumentException("Unknown Comparison Expr: " + operator);
        }

        return rValue;
    }

    /**
     * Creates a conitional AND expr
     */
    private soot.Local createCondAnd(PsiBinaryExpression binary) {

        soot.Local retLocal = lg.generateLocal(soot.BooleanType.v());

        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();

        Value lVal = base().createExpr(binary.getLOperand());
        boolean leftNeedIf = needSootIf(lVal);
        if (!(lVal instanceof soot.jimple.ConditionExpr)) {
            lVal = soot.jimple.Jimple.v().newEqExpr(lVal, soot.jimple.IntConstant.v(0));
        }
        else {
            lVal = reverseCondition((soot.jimple.ConditionExpr)lVal);
            lVal = handleDFLCond((soot.jimple.ConditionExpr)lVal);
        }

        if (leftNeedIf){
            soot.jimple.IfStmt ifLeft = soot.jimple.Jimple.v().newIfStmt(lVal, noop1);
            body.getUnits().add(ifLeft);
            Util.addPsiTags(ifLeft.getConditionBox(), binary.getLOperand());
        }

        soot.jimple.Stmt endNoop = soot.jimple.Jimple.v().newNopStmt();
        Value rVal = base().createExpr(binary.getROperand());
        boolean rightNeedIf = needSootIf(rVal);
        if (!(rVal instanceof soot.jimple.ConditionExpr)) {
            rVal = soot.jimple.Jimple.v().newEqExpr(rVal, soot.jimple.IntConstant.v(0));
        }
        else {
            rVal = reverseCondition((soot.jimple.ConditionExpr)rVal);
            rVal = handleDFLCond((soot.jimple.ConditionExpr)rVal);
        }
        if (rightNeedIf){
            soot.jimple.IfStmt ifRight = soot.jimple.Jimple.v().newIfStmt(rVal, noop1);
            body.getUnits().add(ifRight);
            Util.addPsiTags(ifRight.getConditionBox(), binary.getROperand());
        }
        soot.jimple.Stmt assign1 = soot.jimple.Jimple.v().newAssignStmt(retLocal,
                soot.jimple.IntConstant.v(1));
        body.getUnits().add(assign1);
        soot.jimple.Stmt gotoEnd1 = soot.jimple.Jimple.v().newGotoStmt(endNoop);
        body.getUnits().add(gotoEnd1);

        body.getUnits().add(noop1);

        soot.jimple.Stmt assign2 = soot.jimple.Jimple.v().newAssignStmt(retLocal,
                soot.jimple.IntConstant.v(0));
        body.getUnits().add(assign2);

        body.getUnits().add(endNoop);

        return retLocal;
    }

    /**
     * Creates a conditional OR expr
     */
    private soot.Local createCondOr(PsiBinaryExpression binary) {
        soot.Local retLocal = lg.generateLocal(soot.BooleanType.v());

        //end
        soot.jimple.Stmt endNoop = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();
        Value lVal = base().createExpr(binary.getLOperand());
        //System.out.println("leftval : "+lVal);
        boolean leftNeedIf = needSootIf(lVal);
        if (!(lVal instanceof soot.jimple.ConditionExpr)) {
            lVal = soot.jimple.Jimple.v().newEqExpr(lVal, soot.jimple.IntConstant.v(1));
        }
        else {
            lVal = handleDFLCond((soot.jimple.ConditionExpr)lVal);
        }

        if (leftNeedIf){
            soot.jimple.IfStmt ifLeft = soot.jimple.Jimple.v().newIfStmt(lVal, noop1);
            body.getUnits().add(ifLeft);
            Util.addPsiTags(ifLeft, binary.getLOperand());
            Util.addPsiTags(ifLeft.getConditionBox(), binary.getLOperand());
        }

        Value rVal = base().createExpr(binary.getROperand());
        boolean rightNeedIf = needSootIf(rVal);
        if (!(rVal instanceof soot.jimple.ConditionExpr)) {
            rVal = soot.jimple.Jimple.v().newEqExpr(rVal, soot.jimple.IntConstant.v(1));
        }
        else {
            rVal = handleDFLCond((soot.jimple.ConditionExpr)rVal);
        }
        if (rightNeedIf){
            soot.jimple.IfStmt ifRight = soot.jimple.Jimple.v().newIfStmt(rVal, noop1);
            body.getUnits().add(ifRight);
            Util.addPsiTags(ifRight, binary.getROperand());
            Util.addPsiTags(ifRight.getConditionBox(), binary.getROperand());
        }

        soot.jimple.Stmt assign2 = soot.jimple.Jimple.v().newAssignStmt(retLocal,
                soot.jimple.IntConstant.v(0));
        body.getUnits().add(assign2);
        Util.addPsiTags(assign2, binary);
        soot.jimple.Stmt gotoEnd2 = soot.jimple.Jimple.v().newGotoStmt(endNoop);
        body.getUnits().add(gotoEnd2);

        body.getUnits().add(noop1);

        soot.jimple.Stmt assign3 = soot.jimple.Jimple.v().newAssignStmt(retLocal,
                soot.jimple.IntConstant.v(1));
        body.getUnits().add(assign3);
        Util.addPsiTags(assign3, binary);

        body.getUnits().add(endNoop);

        return retLocal;
    }

    /**
     * Unary Expression Creation
     */
    private soot.Local getUnaryLocal(PsiExpression unary) {
        boolean pre = unary instanceof PsiPrefixExpression;
        PsiExpression expr = Util.getUnaryOperand(unary);
        IElementType op = Util.getUnaryOperationSign(unary).getTokenType();

        if (op == JavaTokenType.PLUSPLUS
                || op == JavaTokenType.MINUSMINUS) {
            if (base().needsAccessor(expr)){
                return base().handlePrivateFieldUnarySet(unary);
            }

            Value left = base().createLHS(expr);

            // do necessary cloning
            Value leftClone = soot.jimple.Jimple.cloneIfNecessary(left);


            soot.Local tmp = lg.generateLocal(left.getType());
            soot.jimple.AssignStmt stmt1 = soot.jimple.Jimple.v().newAssignStmt(tmp, left);
            body.getUnits().add(stmt1);
            Util.addPsiTags(stmt1, unary);

            Value incVal = base().getConstant(left.getType(), 1);

            soot.jimple.BinopExpr binExpr;
            if (op == JavaTokenType.PLUSPLUS){
                binExpr = soot.jimple.Jimple.v().newAddExpr(tmp, incVal);
            }
            else {
                binExpr = soot.jimple.Jimple.v().newSubExpr(tmp, incVal);
            }

            soot.Local tmp2 = lg.generateLocal(left.getType());
            soot.jimple.AssignStmt assign = soot.jimple.Jimple.v().newAssignStmt(tmp2, binExpr);
            body.getUnits().add(assign);

            //if (base().needsAccessor(expr)){
            //  base().handlePrivateFieldSet(expr, tmp2);
            //}
            //else {
            soot.jimple.AssignStmt stmt3 = soot.jimple.Jimple.v().newAssignStmt(leftClone, tmp2);
            body.getUnits().add(stmt3);
            //}

            if (pre) {
                return tmp2;
            } else {
                return tmp;
            }

        }
        else if (op == JavaTokenType.TILDE) {
            soot.jimple.IntConstant int1 = soot.jimple.IntConstant.v(-1);

            soot.Local retLocal = generateLocal(expr.getType());

            Value sootExpr = base().createExpr(expr);

            soot.jimple.XorExpr xor = soot.jimple.Jimple.v().newXorExpr(sootExpr,
                    base().getConstant(sootExpr.getType(), -1));

            Util.addPsiTags(xor.getOp1Box(), expr);
            soot.jimple.Stmt assign1 = soot.jimple.Jimple.v().newAssignStmt(retLocal, xor);

            body.getUnits().add(assign1);

            Util.addPsiTags(assign1, unary);

            return retLocal;
        }
        else if (op == JavaTokenType.MINUS) {
            Value sootExpr = null;
            if (expr instanceof PsiLiteralExpression) {
                PsiLiteralExpression lit = (PsiLiteralExpression) expr;
                Object value = lit.getValue();
                if (value instanceof Integer) {
                    Integer integer = (Integer) value;
                    sootExpr = soot.jimple.IntConstant.v(-integer);
                }
                if (value instanceof Long) {
                    Long aLong = (Long) value;
                    sootExpr = soot.jimple.LongConstant.v(-aLong);

                } else if (value instanceof Double) {
                    Double aDouble = (Double) value;
                    sootExpr = soot.jimple.DoubleConstant.v(-aDouble);

                } else if (value instanceof Float) {
                    Float aFloat = (Float) value;
                    sootExpr = soot.jimple.FloatConstant.v(-(float)aFloat);
                }
            }
            if (sootExpr == null) {
                Value local = base().createExpr(expr);

                soot.jimple.NegExpr negExpr = soot.jimple.Jimple.v().newNegExpr(local);
                sootExpr = negExpr;
                Util.addPsiTags(negExpr.getOpBox(), expr);
            }

            soot.Local retLocal = generateLocal(expr.getType());

            soot.jimple.Stmt assign = soot.jimple.Jimple.v().newAssignStmt(retLocal, sootExpr);

            body.getUnits().add(assign);

            Util.addPsiTags(assign, expr);

            return retLocal;

        }
        else if (op == JavaTokenType.PLUS) {
            soot.Local retLocal = generateLocal(expr.getType());
            Value sootExpr = base().createExpr(expr);
            soot.jimple.Stmt assign = soot.jimple.Jimple.v().newAssignStmt(retLocal, sootExpr);
            body.getUnits().add(assign);

            Util.addPsiTags(assign, expr);

            return retLocal;
        }
        else if (op == JavaTokenType.EXCL) {

            Value local = base().createExpr(expr);

            if (local instanceof soot.jimple.ConditionExpr){
                local = handleCondBinExpr((soot.jimple.ConditionExpr)local);
            }
            soot.jimple.NeExpr neExpr = soot.jimple.Jimple.v().newNeExpr(local,
                    base().getConstant(local.getType(), 0));

            soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();

            soot.jimple.Stmt ifStmt = soot.jimple.Jimple.v().newIfStmt(neExpr, noop1);
            body.getUnits().add(ifStmt);
            Util.addPsiTags(ifStmt, expr);

            soot.Local retLocal = lg.generateLocal(local.getType());

            soot.jimple.Stmt assign1 = soot.jimple.Jimple.v().newAssignStmt(retLocal,
                    base().getConstant(retLocal.getType(), 1));

            body.getUnits().add(assign1);
            Util.addPsiTags(assign1, expr);

            soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();

            soot.jimple.Stmt goto1 = soot.jimple.Jimple.v().newGotoStmt(noop2);

            body.getUnits().add(goto1);

            body.getUnits().add(noop1);

            soot.jimple.Stmt assign2 = soot.jimple.Jimple.v().newAssignStmt(retLocal,
                    base().getConstant(retLocal.getType(), 0));

            body.getUnits().add(assign2);
            Util.addPsiTags(assign2, expr);

            body.getUnits().add(noop2);


            return retLocal;
        }
        else {
            throw new IllegalArgumentException("Unhandled Unary Expr");
        }


    }

    /**
     * Cast Expression Creation
     */
    private Value getCastLocal(PsiTypeCastExpression castExpr){
        // if its already the right type
        PsiExpression operand = castExpr.getOperand();
        if (operand.getType().equals(castExpr.getType())) {
            return base().createExpr(operand);
        }

        Value val = base().createExpr(operand);
        soot.Type type = Util.getSootType(castExpr.getType());

        soot.jimple.CastExpr cast = soot.jimple.Jimple.v().newCastExpr(val, type);
        Util.addPsiTags(cast.getOpBox(), operand);
        soot.Local retLocal = lg.generateLocal(cast.getCastType());
        soot.jimple.Stmt castAssign = soot.jimple.Jimple.v().newAssignStmt(retLocal, cast);
        body.getUnits().add(castAssign);
        Util.addPsiTags(castAssign, castExpr);

        return retLocal;
    }

    /**
     * Procedure Call Helper Methods
     * Returns list of params
     */
    protected List<Value> getSootParams(PsiCallExpression call) {
        List<Value> sootParams = new ArrayList<Value>();
        for (PsiExpression next : call.getArgumentList().getExpressions()) {
            Value nextExpr = base().createExpr(next);
            if (nextExpr instanceof soot.jimple.ConditionExpr) {
                nextExpr = handleCondBinExpr((soot.jimple.ConditionExpr) nextExpr);
            }
            sootParams.add(nextExpr);
        }
        return sootParams;
    }

    /**
     * New Expression Creation
     */
    private soot.Local getNewLocal(PsiNewExpression newExpr) {

        // handle parameters/args
        ArrayList<Value> sootParams = new ArrayList<Value>();
        ArrayList<Type> sootParamsTypes = new ArrayList<Type>();

        PsiClass objType = (PsiClass) newExpr.getClassReference().resolve();

        PsiAnonymousClass anonClass = newExpr.getAnonymousClass();
        if (anonClass != null){
            objType = anonClass;
            // add inner class tags for any anon classes created
            String name = Util.getSootType(objType).toString();
            PsiClass outerType = (PsiClass) anonClass.getBaseClassReference().resolve();
            if (!initialResolver.hasClassInnerTag(body.getMethod().getDeclaringClass(), name)){
                Util.addInnerClassTag(body.getMethod().getDeclaringClass(),
                        name, null, null,
                        outerType.isInterface()
                                ? soot.Modifier.PUBLIC | soot.Modifier.STATIC
                                : Util.getModifier(objType));
            }
        }
        else {
            // not an anon class but actually invoking a new something
            PsiClass outer = objType.getContainingClass();
            if (outer != null){
                String name = Util.getSootType(objType).toString();
                PsiClass outerType = outer;
                if (!initialResolver.hasClassInnerTag(body.getMethod().getDeclaringClass(), name)){
                    Util.addInnerClassTag(body.getMethod().getDeclaringClass(),
                            name, Util.getSootType(outerType).toString(),
                            objType.getName(),
                            outerType.isInterface()
                                    ? soot.Modifier.PUBLIC | soot.Modifier.STATIC
                                    : Util.getModifier(objType));
                }
            }
        }
        soot.RefType sootType = (soot.RefType)Util.getSootType(objType);
        soot.Local retLocal = lg.generateLocal(sootType);
        soot.jimple.NewExpr sootNew = soot.jimple.Jimple.v().newNewExpr(sootType);

        soot.jimple.Stmt stmt = soot.jimple.Jimple.v().newAssignStmt(retLocal, sootNew);
        body.getUnits().add(stmt);
        Util.addPsiTags(stmt, newExpr);


        soot.SootClass classToInvoke = sootType.getSootClass();
        // if no qualifier --> X to invoke is static
        Value qVal = null;
        PsiExpression qualifier = newExpr.getQualifier();
        if (qualifier != null) {
            qVal = base().createExpr(qualifier);
        }
        handleOuterClassParams(sootParams, qVal, sootParamsTypes, objType);
        sootParams.addAll(getSootParams(newExpr));
        sootParamsTypes.addAll(getSootParamsTypes(newExpr));

        addUsedLocalParams(sootParams, sootParamsTypes, objType);

        soot.SootMethodRef methodToInvoke = getMethodFromClass(classToInvoke,
                "<init>", sootParamsTypes, soot.VoidType.v(), false);
        soot.jimple.SpecialInvokeExpr specialInvokeExpr
                = soot.jimple.Jimple.v().newSpecialInvokeExpr(retLocal,
                methodToInvoke, sootParams);

        soot.jimple.Stmt invokeStmt = soot.jimple.Jimple.v().newInvokeStmt(specialInvokeExpr);

        body.getUnits().add(invokeStmt);
        Util.addPsiTags(invokeStmt, newExpr);

        int numParams = 0;
        for (PsiExpression expr : newExpr.getArgumentList().getExpressions()) {
            Util.addPsiTags(specialInvokeExpr.getArgBox(numParams), expr);
            numParams++;
        }

        return retLocal;
    }

    /**
     * Call Expression Creation
     */
    private soot.Local getCallLocal(PsiMethodCallExpression call){
        PsiReferenceExpression methExp = call.getMethodExpression();
        // handle receiver/target
        PsiExpression qualifier = methExp.getQualifierExpression();
        if (qualifier instanceof PsiSuperExpression
                && ((PsiSuperExpression) qualifier).getQualifier() != null) {
            return getSpecialSuperQualifierLocalForMethod(call);
        }

        List<Value> sootParams = getSootParams(call);

        soot.SootMethodRef callMethod = base().getSootMethodRef(call);

        soot.RefType objRefType = soot.RefType.v("java.lang.Object");
        PsiMethod method = call.resolveMethod();
        PsiClass methodCls = method.getContainingClass();

        // receiver.getType is null if the receiver is a classname for a static
        // method call like System in System.out.println
        soot.Local baseLocal = null;
        boolean isStatic = method.hasModifierProperty("static");
        if (qualifier == null) {
            // the method is either a call to this.something, or it's statically
            // imported
            if (!isStatic) {
                baseLocal = specialThisLocal;
            }

        } else {
            if (qualifier.getType() != null) {
                baseLocal = (soot.Local)base().getBaseLocal(qualifier);
            } else {
                // it's a static method call
            }
        }

        soot.SootClass receiverTypeClass;
        Type sootRecType = null;
        if (Util.getSootType(methodCls).equals(objRefType)){
            sootRecType = objRefType;
            receiverTypeClass = soot.Scene.v().getSootClass("java.lang.Object");
        }
        else {
            if (qualifier != null) {
                PsiType recvType = qualifier.getType();
                if (recvType != null) {
                    sootRecType = Util.getSootType(recvType);
                }
            }
            if (sootRecType == null){
                sootRecType = Util.getSootType(methodCls);
            }
            if (sootRecType instanceof soot.RefType){
                receiverTypeClass = ((soot.RefType)sootRecType).getSootClass();
            }
            else if (sootRecType instanceof soot.ArrayType){
                receiverTypeClass = soot.Scene.v().getSootClass("java.lang.Object");
            }
            else {
                throw new IllegalArgumentException("call target problem: "+call);
            }
        }

        boolean isPrivateAccess = false;
        if (needsAccessor(call)) {
            soot.SootClass containingClass = ((soot.RefType)Util.getSootType(
                    methodCls)).getSootClass();
            soot.SootClass classToAddMethTo = containingClass;

            if (method.hasModifierProperty("protected")){

                if (initialResolver.hierarchy() == null){
                    initialResolver.hierarchy(new soot.FastHierarchy());
                }
                soot.SootClass addToClass;
                if (body.getMethod().getDeclaringClass().hasOuterClass()){
                    addToClass = body.getMethod().getDeclaringClass().getOuterClass();

                    FastHierarchy hierarchy = initialResolver.hierarchy();
                    while (!hierarchy.canStoreType(containingClass.getType(), addToClass.getType())){
                        if (addToClass.hasOuterClass()){
                            addToClass = addToClass.getOuterClass();
                        }
                        else {
                            break;
                        }
                    }
                } else {
                    addToClass = containingClass;
                }
                classToAddMethTo = addToClass;
            }

            callMethod = addGetMethodAccessMeth(classToAddMethTo, call).makeRef();
            if (!isStatic){
                if (qualifier != null && qualifier.getType() != null){
                    assert baseLocal != null;
                    sootParams.add(0, baseLocal);
                }

                else if (body.getMethod().getDeclaringClass().declaresFieldByName("this$0")){
                    sootParams.add(0, getThis(Util.getSootType(
                            methodCls)));
                }
                else {
                    assert baseLocal != null;
                    sootParams.add(0, baseLocal);
                }
            }
            isPrivateAccess = true;
        }

        soot.jimple.InvokeExpr invokeExpr;
        Jimple jimple = Jimple.v();
        if (isPrivateAccess){
            // for accessing private methods in outer class -> always static
            invokeExpr = jimple.newStaticInvokeExpr(callMethod, sootParams);
        }
        else if (soot.Modifier.isInterface(receiverTypeClass.getModifiers())
                && method.hasModifierProperty("abstract")) {
            // if reciever class is interface and method is abstract -> interface
            assert baseLocal != null;
            invokeExpr = jimple.newInterfaceInvokeExpr(baseLocal, callMethod, sootParams);
        }
        else if (isStatic){
            // if flag isStatic -> static invoke
            invokeExpr = jimple.newStaticInvokeExpr(callMethod, sootParams);
        }
        else if (method.hasModifierProperty("private")){
            // if flag isPrivate -> special invoke
            assert baseLocal != null;
            invokeExpr = jimple.newSpecialInvokeExpr(baseLocal, callMethod, sootParams);
        }
        else if (qualifier instanceof PsiSuperExpression){
            // receiver is special super -> special
            assert baseLocal != null;
            invokeExpr = jimple.newSpecialInvokeExpr(baseLocal, callMethod, sootParams);
        }
        else {
            // else virtual invoke
            assert baseLocal != null;
            invokeExpr = jimple.newVirtualInvokeExpr(baseLocal, callMethod, sootParams);
        }

        int numParams = 0;
        for (PsiExpression expr : call.getArgumentList().getExpressions()) {
            Util.addPsiTags(invokeExpr.getArgBox(numParams), expr);
            numParams++;
        }

        if (invokeExpr instanceof soot.jimple.InstanceInvokeExpr) {
            Util.addPsiTags(((soot.jimple.InstanceInvokeExpr)invokeExpr).getBaseBox(),
                    qualifier);
        }

        // create an assign stmt so invoke can be used somewhere else

        if (invokeExpr.getMethodRef().returnType().equals(soot.VoidType.v())) {
            soot.jimple.Stmt invoke = jimple.newInvokeStmt(invokeExpr);
            body.getUnits().add(invoke);
            Util.addPsiTags(invoke, call);
            return null;
        }
        else {
            soot.Local retLocal = lg.generateLocal(invokeExpr.getMethodRef().returnType());

            soot.jimple.AssignStmt assignStmt = jimple.newAssignStmt(retLocal, invokeExpr);

            // add assign stmt to body
            body.getUnits().add(assignStmt);

            Util.addPsiTags(assignStmt, call);
            Util.addPsiTags(assignStmt.getRightOpBox(), call);
            return retLocal;
        }
    }

    /**
     * NewArray Expression Creation
     */
    private soot.Local getNewArrayLocal(PsiNewExpression newArrExpr) {

        Type sootType = Util.getSootType(newArrExpr.getType());

//System.out.println("creating new array of type: "+sootType);
        PsiExpression[] dims = newArrExpr.getArrayDimensions();
//TODO: test additionaldims for newarraylocal
        int additionalDims = newArrExpr.getType().getArrayDimensions() - dims.length;
        soot.jimple.Expr expr;
        if (dims.length == 1) {

            Value dimLocal;
            if (additionalDims == 1) {
                dimLocal = soot.jimple.IntConstant.v(1);
            }
            else {
                dimLocal = base().createExpr(dims[0]);
            }
            soot.jimple.NewArrayExpr newArrayExpr = Jimple.v().newNewArrayExpr(
                    ((soot.ArrayType)sootType).getElementType(), dimLocal);
            expr = newArrayExpr;
            if (additionalDims != 1){
                Util.addPsiTags(newArrayExpr.getSizeBox(), dims[0]);
            }
        }
        else {

            List<Value> valuesList = new ArrayList<Value>();
            for (PsiExpression expr1 : dims) {
                valuesList.add(base().createExpr(expr1));
            }

            if (additionalDims != 0) {
                valuesList.add(soot.jimple.IntConstant.v(additionalDims));
            }
            soot.jimple.NewMultiArrayExpr newMultiArrayExpr
                    = Jimple.v().newNewMultiArrayExpr((soot.ArrayType)sootType,
                    valuesList);


            expr = newMultiArrayExpr;
            int counter = 0;
            for (PsiExpression expression : dims) {
                Util.addPsiTags(newMultiArrayExpr.getSizeBox(counter), expression);
                counter++;
            }
        }

        soot.Local retLocal = lg.generateLocal(sootType);

        soot.jimple.AssignStmt stmt = Jimple.v().newAssignStmt(retLocal, expr);

        body.getUnits().add(stmt);

        Util.addPsiTags(stmt, newArrExpr);
        Util.addPsiTags(stmt.getRightOpBox(), newArrExpr);

// handle array init if one exists
        PsiArrayInitializerExpression initializer = newArrExpr.getArrayInitializer();
        if (initializer != null) {
            Value initVal = getArrayInitLocal(initializer, newArrExpr.getType());
            soot.jimple.AssignStmt initStmt = Jimple.v().newAssignStmt(retLocal, initVal);

            body.getUnits().add(initStmt);
        }

        return retLocal;

    }

    /**
     * Array Ref Expression Creation - LHS
     */
    protected Value getArrayRefLocalLeft(PsiArrayAccessExpression arrayRefExpr) {
        PsiExpression array = arrayRefExpr.getArrayExpression();
        PsiExpression index = arrayRefExpr.getIndexExpression();

        soot.Local arrLocal = (soot.Local)base().createExpr(array);
        Value arrAccess = base().createExpr(index);

        soot.Local retLocal = generateLocal(arrayRefExpr.getType());

        soot.jimple.ArrayRef ref = Jimple.v().newArrayRef(arrLocal, arrAccess);

        Util.addPsiTags(ref.getBaseBox(), array);
        Util.addPsiTags(ref.getIndexBox(), index);
        return ref;
    }

    /**
     * Array Ref Expression Creation
     */
    private Value getArrayRefLocal(PsiArrayAccessExpression arrayRefExpr) {

        PsiExpression array = arrayRefExpr.getArrayExpression();
        PsiExpression access = arrayRefExpr.getIndexExpression();

        soot.Local arrLocal = (soot.Local)base().createExpr(array);
        Value arrAccess = base().createExpr(access);

        soot.Local retLocal = generateLocal(arrayRefExpr.getType());

        soot.jimple.ArrayRef ref = Jimple.v().newArrayRef(arrLocal, arrAccess);

        Util.addPsiTags(ref.getBaseBox(), array);
        Util.addPsiTags(ref.getIndexBox(), access);

        soot.jimple.Stmt stmt = Jimple.v().newAssignStmt(retLocal, ref);
        body.getUnits().add(stmt);
        Util.addPsiTags(stmt, arrayRefExpr);


        return retLocal;
    }

    private soot.Local getSpecialSuperQualifierLocalForField(PsiReferenceExpression expr){
        soot.SootClass classToInvoke;
        List<Value> methodParams = new ArrayList<Value>();
        PsiSuperExpression qualifier = (PsiSuperExpression) expr.getQualifierExpression();
        PsiClass superQualifierCls = (PsiClass) qualifier.getQualifier().resolve();
        classToInvoke = ((soot.RefType)Util.getSootType(superQualifierCls)).getSootClass();

// make an access method
        soot.SootMethod methToInvoke = makeSuperAccessMethodForField(classToInvoke, expr);
// invoke it
        soot.Local classToInvokeLocal = Util.getThis(classToInvoke.getType(),
                body, getThisMap, lg);
        methodParams.add(0, classToInvokeLocal);

        soot.jimple.InvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(
                methToInvoke.makeRef(), methodParams);

// return the local of return type if  not void
        if (!methToInvoke.getReturnType().equals(soot.VoidType.v())){
            soot.Local retLocal = lg.generateLocal(methToInvoke.getReturnType());
            soot.jimple.AssignStmt stmt = Jimple.v().newAssignStmt(retLocal, invokeExpr);
            body.getUnits().add(stmt);

            return retLocal;
        }
        else {
            body.getUnits().add(Jimple.v().newInvokeStmt(invokeExpr));
            return null;
        }
    }

    private soot.Local getSpecialSuperQualifierLocalForMethod(PsiMethodCallExpression expr){
        soot.SootClass classToInvoke;
        List<Value> methodParams = new ArrayList<Value>();
        classToInvoke = ((soot.RefType)Util.getSootType(
                expr.getMethodExpression().getType())).getSootClass();
        methodParams = getSootParams(expr);
// make an access method
        soot.SootMethod methToInvoke = makeSuperAccessMethodForMethod(classToInvoke, expr);
// invoke it
        soot.Local classToInvokeLocal = Util.getThis(classToInvoke.getType(),
                body, getThisMap, lg);
        methodParams.add(0, classToInvokeLocal);

        soot.jimple.InvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(
                methToInvoke.makeRef(), methodParams);

// return the local of return type if  not void
        if (!methToInvoke.getReturnType().equals(soot.VoidType.v())){
            soot.Local retLocal = lg.generateLocal(methToInvoke.getReturnType());
            soot.jimple.AssignStmt stmt = Jimple.v().newAssignStmt(retLocal, invokeExpr);
            body.getUnits().add(stmt);

            return retLocal;
        }
        else {
            body.getUnits().add(Jimple.v().newInvokeStmt(invokeExpr));
            return null;
        }
    }
    private soot.Local getThisLocal(PsiThisExpression specialExpr) {//System.out.println("this is special this: "+specialExpr);
        PsiJavaCodeReferenceElement qualifier = specialExpr.getQualifier();
        if (qualifier == null) {
            return specialThisLocal;
        }
        else {
            //TODO: test qualified this
            return getThis(Util.getSootType((PsiClass) qualifier.resolve()));
        }
    }

    private soot.Local getSuperLocal(PsiSuperExpression specialExpr) {
        PsiJavaCodeReferenceElement qualifier = specialExpr.getQualifier();
        if (qualifier == null){
            return specialThisLocal;
        }
        else {
            //TODO: fix super reference
            // this isn't enough
            // need to getThis for the type which may be several levels up
            // add access$N method to class of the type which returns
            // field or method wanted
            // invoke it
            // and it needs to be called specially when getting fields
            // or calls because need to know field or method to access
            // as it access' a field or meth in the super class of the
            // outer class refered to by the qualifier
            return getThis(Util.getSootType((PsiClass) qualifier.resolve()));
        }
    }

    private soot.SootMethod makeSuperAccessMethodForField(soot.SootClass classToInvoke,
            PsiReferenceExpression memberToAccess){
        String name = "access$"+initialResolver.getNextPrivateAccessCounter()+"00";
        ArrayList<Type> paramTypes = new ArrayList<Type>();
        paramTypes.add(classToInvoke.getType());

        soot.SootMethod meth;
        meth = new soot.SootMethod(name, paramTypes, Util.getSootType(
                memberToAccess.getType()), soot.Modifier.STATIC);
        PrivateFieldAccMethodSource fSrc = new PrivateFieldAccMethodSource(
                Util.getSootType(memberToAccess.getType()),
                memberToAccess.getReferenceName(),
                ((PsiField) memberToAccess.resolve()).hasModifierProperty("static"),
                ((soot.RefType) Util.getSootType(memberToAccess.getQualifierExpression()
                        .getType())).getSootClass()
        );
        classToInvoke.addMethod(meth);
        meth.setActiveBody(((soot.MethodSource)fSrc).getBody(meth, null));
        meth.addTag(new soot.tagkit.SyntheticTag());
        return meth;
    }

    private soot.SootMethod makeSuperAccessMethodForMethod(soot.SootClass classToInvoke,
            PsiMethodCallExpression call){
        String name = "access$"+initialResolver.getNextPrivateAccessCounter()+"00";
        ArrayList<Type> paramTypes = new ArrayList<Type>();
        paramTypes.add(classToInvoke.getType());

        soot.SootMethod meth;
        soot.MethodSource src;
        PsiMethodCallExpression methToAccess = call;
        paramTypes.addAll(getSootParamsTypes(methToAccess));
        PsiMethod method = methToAccess.resolveMethod();
        meth = new soot.SootMethod(name, paramTypes, Util.getSootType(
                method.getReturnType()), soot.Modifier.STATIC);
        PrivateMethodAccMethodSource mSrc = new PrivateMethodAccMethodSource(
                method);
        src = mSrc;
        classToInvoke.addMethod(meth);
        meth.setActiveBody(src.getBody(meth, null));
        meth.addTag(new soot.tagkit.SyntheticTag());
        return meth;
    }

    /**
     * InstanceOf Expression Creation
     */
    private soot.Local getInstanceOfLocal(PsiInstanceOfExpression instExpr) {

        Type sootType = Util.getSootType(instExpr.getCheckType().getType());

        Value local = base().createExpr(instExpr.getOperand());

        soot.jimple.InstanceOfExpr instOfExpr = Jimple.v().newInstanceOfExpr(local, sootType);

        soot.Local lhs = lg.generateLocal(soot.BooleanType.v());

        soot.jimple.Stmt instAssign = Jimple.v().newAssignStmt(lhs, instOfExpr);
        body.getUnits().add(instAssign);
        Util.addPsiTags(instAssign, instExpr);

        Util.addPsiTags(instOfExpr.getOpBox(), instExpr.getOperand());
        return lhs;
    }

    /**
     * Condition Expression Creation - can maybe merge with If
     */
    private soot.Local getConditionalLocal(PsiConditionalExpression condExpr){

        // handle cond
        PsiExpression condition = condExpr.getCondition();
        Value sootCond = base().createExpr(condition);
        boolean needIf = needSootIf(sootCond);
        if (!(sootCond instanceof soot.jimple.ConditionExpr)) {
            sootCond = Jimple.v().newEqExpr(sootCond, soot.jimple.IntConstant.v(0));
        }
        else {
            sootCond = reverseCondition((soot.jimple.ConditionExpr)sootCond);
            sootCond = handleDFLCond((soot.jimple.ConditionExpr)sootCond);
        }

        soot.jimple.Stmt noop1 = Jimple.v().newNopStmt();
        if (needIf){
            soot.jimple.IfStmt ifStmt = Jimple.v().newIfStmt(sootCond, noop1);

            body.getUnits().add(ifStmt);
            Util.addPsiTags(ifStmt, condExpr);
            Util.addPsiTags(ifStmt.getConditionBox(), condition);
        }

        soot.Local retLocal = generateLocal(condExpr.getType());

// handle consequence
        PsiExpression consequence = condExpr.getThenExpression();

        Value conseqVal = base().createExpr(consequence);
        if (conseqVal instanceof soot.jimple.ConditionExpr) {
            conseqVal = handleCondBinExpr((soot.jimple.ConditionExpr)conseqVal);
        }
        soot.jimple.AssignStmt conseqAssignStmt = Jimple.v().newAssignStmt(retLocal, conseqVal);
        body.getUnits().add(conseqAssignStmt);
        Util.addPsiTags(conseqAssignStmt, condExpr);
        Util.addPsiTags(conseqAssignStmt.getRightOpBox(), consequence);

        soot.jimple.Stmt noop2 = Jimple.v().newNopStmt();
        soot.jimple.Stmt goto1 = Jimple.v().newGotoStmt(noop2);
        body.getUnits().add(goto1);

// handle alternative

        body.getUnits().add(noop1);

        PsiExpression alternative = condExpr.getElseExpression();
        if (alternative != null){
            Value altVal = base().createExpr(alternative);
            if (altVal instanceof soot.jimple.ConditionExpr) {
                altVal = handleCondBinExpr((soot.jimple.ConditionExpr)altVal);
            }
            soot.jimple.AssignStmt altAssignStmt = Jimple.v().newAssignStmt(retLocal, altVal);
            body.getUnits().add(altAssignStmt);
            Util.addPsiTags(altAssignStmt, condExpr);
//            Util.addPsiTags(altAssignStmt, alternative);
            Util.addPsiTags(altAssignStmt.getRightOpBox(), alternative);
        }
        body.getUnits().add(noop2);

        return retLocal;
    }

    protected abstract boolean needsOuterClassRef(PsiClass typeToInvoke);

    protected abstract void handleOuterClassParams(List<Value> sootParams,
            Value qVal,  List<Type> sootParamsTypes,
            PsiClass typeToInvoke);
}
