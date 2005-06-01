/* Soot - a J*va Optimization Framework
 * Copyright (C) 2004 Jennifer Lhotak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package net.kano.nully.plugin.psiToJimple;

import com.intellij.psi.PsiVariable;

import java.util.*;

public class AnonLocalClassInfo{

    private boolean inStaticMethod;
    private List<IdentityKey<PsiVariable>> finalLocalsAvail;
    private List<IdentityKey<PsiVariable>> finalLocalsUsed;

    public boolean inStaticMethod(){
        return inStaticMethod;
    }
    public void inStaticMethod(boolean b){
        inStaticMethod = b;
    }

    public List<IdentityKey<PsiVariable>> finalLocalsAvail(){
        return finalLocalsAvail;
    }
    public void finalLocalsAvail(List<IdentityKey<PsiVariable>> list){
        finalLocalsAvail = list;
    }

    public List<IdentityKey<PsiVariable>> finalLocalsUsed(){
        return finalLocalsUsed;
    }
    public void finalLocalsUsed(List<IdentityKey<PsiVariable>> list){
        finalLocalsUsed = list;
    }

    public String toString(){
        StringBuffer sb = new StringBuffer();
        sb.append("static: ");
        sb.append(inStaticMethod);
        sb.append(" finalLocalsAvail: ");
        sb.append(finalLocalsAvail);
        sb.append(" finalLocalsUsed: ");
        sb.append(finalLocalsUsed);
        return sb.toString();
    }
}
