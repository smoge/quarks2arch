+ Dictionary { 
	
	blend { |that, blend = 0.5, fill = true, specs| 
		
		var commonKeys, mySingleKeys, otherSingleKeys;
		var myKeys =  this.keys, otherKeys = that.keys;
		var res = (); 
	
		if (myKeys == otherKeys) { 
			commonKeys = myKeys;
		} {
			commonKeys = myKeys.sect(otherKeys);
			mySingleKeys = myKeys.difference(otherKeys);
			otherSingleKeys = otherKeys.difference(myKeys);
		};
		
		commonKeys.do { |key|
			var spec = if (specs.notNil) { specs[key] };
			if (spec.notNil) { 
				res.put(key, spec.map(blend(spec.unmap(this[key]), spec.unmap(that[key]), blend)));
			} { 
				res.put(key, blend(this[key], that[key], blend));
			};
		};
		if (fill) { 
			mySingleKeys.do { |key| res.put(key, this[key]) };
			otherSingleKeys.do { |key| res.put(key, that[key]) };
		};
		^res
	}
}