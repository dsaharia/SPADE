/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.utility;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Series<T extends Comparable<T>, V>{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private List<SimpleEntry<T, V>> series = new ArrayList<SimpleEntry<T, V>>();
	
	private boolean sorted = false;
	private Comparator<SimpleEntry<T, V>> comparator = new Comparator<SimpleEntry<T,V>>(){
		@Override
		public int compare(SimpleEntry<T, V> o1, SimpleEntry<T, V> o2){
			if(o1 == null && o2 == null){
				return 0;
			}else if(o1 == null && o2 != null){
				return -1;
			}else if(o1 != null && o2 == null){
				return 1;
			}else{
				T t1 = o1.getKey();
				T t2 = o2.getKey();
				if(t1 == null && t2 == null){
					return 0;
				}else if(t1 == null && t2 != null){
					return -1;
				}else if(t1 != null && t2 == null){
					return 1;
				}else{
					return t1.compareTo(t2);
				}
			}
		}
	};	
	
	public void add(T t, V v){
		if(t == null){
			logger.log(Level.WARNING, "NOT ADDED. Key cannot be null. Value = " + v);
		}else{
			SimpleEntry<T, V> entry = new SimpleEntry<T, V>(t, v);
			series.add(entry);
		}
	}
	
	// Caller must make sure about not null
	public V getBestMatch(T t){
		if(t == null){
			logger.log(Level.WARNING, "Key to look up cannot be null.");
		}else{
			if(!sorted){
				sorted = true;
				Collections.sort(series, comparator);
			}
			// Looking up in reverse because we want the last associated value for that key.
			for(int a = series.size() - 1; a > -1; a--){
				SimpleEntry<T, V> entry = series.get(a);
				T time = entry.getKey();
				if(t.compareTo(time) >= 0){
					return entry.getValue();
				}
			}
		}
		return null; // none matched
	}
	
	public static void main(String[] args){
		Series<Double, String> series = new Series<>();
		series.add(1.0, "a1");
		series.add(2.0, "a2");
		series.add(3.0, "a3");
		series.add(null, "a4");
		series.add(5.0, "a5");
		series.add(6.0, "a6");
		series.add(7.0, "a7");
		
		System.out.println(series.getBestMatch(0.9));
		new B();
	}
	
}

abstract class A{
	
	public A(){
		System.out.println("A constructor");
		p();
	}
	
	public void p(){
		System.out.println("A:p");
	}
	
}

class B extends A{
	public B(){
		System.out.println("B constructor");
	}
	public void p(){
		super.p();
		System.out.println("B:p");
	}
}