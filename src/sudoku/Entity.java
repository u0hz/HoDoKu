/*
 * Copyright (C) 2008/09  Bernhard Hobiger
 *
 * This file is part of HoDoKu.
 *
 * HoDoKu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HoDoKu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HoDoKu. If not, see <http://www.gnu.org/licenses/>.
 */

package sudoku;

/**
 *
 * @author Bernhard Hobiger
 */
public class Entity implements Cloneable {
    public int entityName;
    public int entityNumber;
    
    public Entity() {
        
    }
    
    public Entity(int name, int number) {
        entityName = name;
        entityNumber = number;
    }
    
    @Override
    public boolean equals(Object o) {
        Entity c = (Entity) o;
        if (entityName == c.entityName && entityNumber == c.entityNumber) {
            return true;
        }
        return false;
    }
    
    public int getEntityName() {
        return entityName;
    }
    
    public void setEntityName(int entityName) {
        this.entityName = entityName;
    }
    
    public int getEntityNumber() {
        return entityNumber;
    }
    
    public void setEntityNumber(int entityNumber) {
        this.entityNumber = entityNumber;
    }
}
