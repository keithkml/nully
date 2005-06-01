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

import java.util.*;

public class BiMap<K,V> {

    HashMap<K,V> keyVal;
    HashMap<V,K> valKey;
    
    public BiMap(){
    }

    public void put(K key, V val){
        if (keyVal == null){
            keyVal = new HashMap<K, V>();
        }
        if (valKey == null){
            valKey = new HashMap<V, K>();
        }

        keyVal.put(key, val);
        valKey.put(val, key);
        
    }

    public K getKey(V val){
        if (valKey == null) return null;
        return valKey.get(val);
    }

    public V getVal(K key){
        if (keyVal == null) return null;
        return keyVal.get(key);
    }

    public boolean containsKey(K key){
        if (keyVal == null) return false;
        return keyVal.containsKey(key);
    }
    
    public boolean containsVal(V val){
        if (valKey == null) return false;
        return valKey.containsKey(val);
    }
}
