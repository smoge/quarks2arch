

Instr  {

	classvar <dir;

	var  <>name, <>func, <>specs, <>outSpec, >path;
	var <explicitSpecs;// specs that were explicitly stated on construction (not guessed)

	// specs are optional : can be guessed from the argnames
	// outSpec is optional : can be determined by evaluating the func and examining the result
	*new { arg name, func, specs, outSpec;
		var previous;
		if(func.isNil,{ ^this.at(name) });
		name = this.symbolizeName(name);
		previous = Library.atList(name.copy.addFirst(this));
		if(previous.notNil,{
			if(previous.isKindOf(Instr).not,{
				Error("The Instr name address " + name +
					"is already occupied by a branch node. You may only add new Instr to the leaves").throw;
			});
			previous.func = func;
			previous.init(specs,outSpec);
			previous.changed(this);
			^previous
		});
		^super.newCopyArgs(name,func).init(specs,outSpec)
	}
	*at { arg  name;
		^this.objectAt(name);
	}
	*loadAll {
	    this.prLoadDir(this.dir);
		this.prLoadDir(Platform.userExtensionDir ++ "/quarks/*/Instr");
	}
	*prLoadDir { arg dir;
	    var paths;
	    paths = (dir +/+ "*").pathMatch.reject { |path| path.splitext[1] == "sc" };
	    paths.do { |path|
	        if(path.last == $/,{
	            this.prLoadDir(path)
	        },{
    			{
				path.loadPath(false);
    			}.try({ arg err;
    				("ERROR while loading " + path).postln;
    				err.throw;
    			});
    		});
		};
	}
	*clearAll {
		Library.global.removeAt(this)
	}

	*ar { arg name, args;
		var instr;
		instr=this.at(name);
		if(instr.isNil,{
			die("Instr not found !!"
					+ name.asCompileString + "in Meta_Instr:ar");
		},{
			^instr.valueArray(args)
		})
	}
	ar { arg ... inputs;
		^func.valueArray(inputs);
	}
	value { arg inputs;
		^func.valueArray(inputs ? [])
	}
	valueEnvir { arg inputs;
		^func.valueArrayEnvir(inputs ? []);
	}
	valueArray { arg inputs;
		^func.valueArray(inputs)
	}
	*kr { arg name, args;
		^this.ar(name,args)
	}
	kr { arg ... inputs;
		^func.valueArrayEnvir(inputs);
	}
	next { arg ... inputs;
		^func.valueArray(inputs)
	}

	// set the directory where your library of Instr is to be found
	*dir_ { arg p;
		dir = p.standardizePath.withTrailingSlash;
	}

	rate {
		^if(outSpec.notNil,{
			outSpec.rate;
		},{
			// if you aren't audio, you must specify an outSpec
			\audio
		})
	}
	numChannels {
		^if(outSpec.notNil,{
			outSpec.numChannels
		},{ // if you are more than one channel, you must specify an outSpec
			1
		});
	}
	path { ^path }

	maxArgs { ^this.argsSize }
	argsSize { ^func.def.argNames.size }
	argNames { ^(func.def.argNames ? []).asList }
	defArgs { ^(func.def.prototypeFrame ? []).asList }

	argNameAt { arg i;
		var nn;
		nn=func.def.argNames;
		^if(nn.notNil,{nn.at(i)},nil);
	}
	defArgAt { arg i;
		var nn;
		nn=func.def.prototypeFrame;
		^nn.at(i)
	}
	// the default value supplied in the function
	initAt { arg i;
		^(this.defArgAt(i) ?? {this.specs.at(i).tryPerform(\default)})
	}

	defName { ^this.class.symbolizeName(name).collect(_.asString).join($.) }
	asSynthDef { arg args,outClass=\Out;
		var synthDef;
		synthDef = InstrSynthDef.new;
		synthDef.build(this,args,outClass);
		^synthDef
	}

	prepareToBundle { arg group,bundle;
		this.asSynthDef.prepareToBundle(group,bundle);
	}

	writeDefFile { arg dir;
		this.asSynthDef.writeDefFile(dir);
	}
	write { arg dir;
		var synthDef;
		synthDef = this.asSynthDef;
		synthDef.writeDefFile(dir);
	}
	// for use in patterns
	store {
		var args;
		args = this.specs.collect({ arg spec,i;
				if(spec.rate == \control or: spec.rate == \stream,{
					IrNumberEditor(this.initAt(i),spec);
				},{
					spec.defaultControl(this.initAt(i))
				})
			});
		^this.asSynthDef(args).store
	}
	asDefName {
		^this.store.name
	}
	funcDef { ^func.def }
	test { arg ... args;
		var p;
		p = Patch(this.name,args);
		p.topGui;
		^p
	}
	play { arg ... args;
		^Patch(this.name,args).play
	}
	plot { arg args,duration=5.0;
		^Patch(this.name,args).plot(duration)
	}
	*choose { arg start;
		// this is only choosing from Instr in memory,
		// it is not loading all possible Instr from file
		^if(start.isNil,{
			Library.global.choose(this)
		},{
			Library.global.performList(\choose,([this] ++ this.symbolizeName(start)))
		})
	}
	*leaves { arg startAt; // address array
		var dict;
		if(startAt.isNil,{
			dict = Library.global.at(this);
			if(dict.isNil,{ ^[] });
		},{
			dict = this.at(this.symbolizeName(startAt).first);
		});
		if(dict.isNil,{
			Error("Instr address not found:" + startAt.asCompileString).throw;
		});
		^Library.global.prNestedValuesFromDict(dict).flat
	}
	// select instr in your entire library that output the given spec
	*selectBySpec { arg outSpec;
		outSpec = outSpec.asSpec;
		^this.leaves.select({ |ins| ins.outSpec == outSpec })
	}
	// i'm feeling lucky...
	*chooseBySpec { arg outSpec;
		^this.selectBySpec(outSpec).choose
	}

	//private
	*put { arg instr;
		^Library.putList([this,this.symbolizeName(instr.name),instr].flatten )
	}
	*remove { arg instr;
		^Library.global.removeAt([this,this.symbolizeName(instr.name)].flatten )
	}
	// bulk insert an orchestra of instr
	*orc { arg name, pairs, outSpec = \audio;
		forBy(0,pairs.size-1,2,	{ arg i;
			this.new( [name,pairs@i ],pairs@(i+1),nil,outSpec)
		})
	}

	*symbolizeName { arg name;
		if(name.isString,{
			^name.split($.).collect(_.asSymbol);
		 });
	 	if(name.isKindOf(Symbol),{
			^[name];
		});
		if(name.isSequenceableCollection,{
			^name.collect(_.asSymbol);
		});
		error("Invalid name for Instr : "++name);
	}
	*isDefined { arg name;
		^Library.atList([this] ++ this.symbolizeName(name)).notNil;
	}		
	*objectAt { arg name;
		var symbolized,search;
		symbolized = this.symbolizeName(name);
		search = Library.atList([this] ++ symbolized);
		if(search.notNil,{ ^search });
		symbolized.debug("Instr not found, loading file...");
		this.findFileFor(symbolized);

		// its either loaded now or its nil
		^Library.atList([this] ++ symbolized);
	}
	*findFileFor { arg symbolized;
		var quarkInstr,found;
		// the user's primary Instr directory
		found = this.findFileInDir(symbolized,this.dir);
		if(found.notNil,{ ^found });

		// look in each quark with an Instr directory
		quarkInstr = (Platform.userExtensionDir ++ "/quarks/*/Instr").pathMatch;
		quarkInstr.do({ |path|
			found = this.findFileInDir(symbolized,path);
			if(found.notNil,{ ^found });
		});
		^nil
	}
	// .rtf .txt .sc .scd or plain
	*findFileInDir { arg symbolized, rootPath,
						fullInstrName;// only needed for recursion
		var pathParts,pathPartsFirst;

		pathParts = symbolized.collect(_.asString);

		pathPartsFirst = pathParts.first;
		if(fullInstrName.isNil,{ fullInstrName = symbolized.copy });

		// if its a multi-part name then could be
		// [\synths,\stereo,\SinOsc,\pmod]
		// possible files:
		// synths.scd
		// or synths/stereo.scd
		// or synths/stereo/SinOsc/pmod.scd

		(rootPath++"*").pathMatch.do({ |path|
			var file,orcname,symbols,pn;
			file = path.copyRange(rootPath.size,path.size-1);
			if(file.last == $/,{
				if(file.copyRange(0,file.size-2) == pathPartsFirst,{
					^this.findFileInDir(symbolized.copyRange(1,symbolized.size-1),
										rootPath ++ file,
										fullInstrName );
				});
			},{
			    pn = PathName(file);
			    if(["scd","rtf",""].includesEqual(pn.extension),{
    				orcname = pn.fileNameWithoutExtension;
    				if(orcname == pathPartsFirst,{
    				    ("Loading instr file:" + path).debug(orcname);
    					
    					// compiles and creates all Instr
    					// which are now findable in the Library
    					path.load;
					
    					// set path on all those within this file that we just loaded
    					symbols = [];
    					fullInstrName.any({ |n|
    						symbols = symbols.add(n.asSymbol);
    						n.asSymbol === orcname.asSymbol
    					});
    					Instr.leaves(symbols).do({ |instr| instr.path = path });
    					^path
    				});
    			})
			});
		});

		^nil
	}
	dotNotation { // "dir.subdir.file.instrName"
		^String.streamContents({ arg s;
			name.do({ arg n,i;
				if(i > 0,{ s << $. });
				s << n;
			})
		})
	}

	// this is a tilda delimited version of the name
	asSingleName {
		^String.streamContents({ arg s;
			name.do({ arg n,i;
				if(i > 0,{ s << $~ });
				s << n;
			})
		})
	}
	*singleNameAsNames { arg singleName;
		^singleName.asString.split($~).collect({ arg n; n.asSymbol })
	}


	asString { ^"%(%)".format(this.class.name, this.defName.asCompileString) }
	storeArgs {
		if(this.path.notNil,{
			^[this.dotNotation]
		},{
			^[this.dotNotation,this.func,this.specs,this.outSpec]
		});
	}
	copy { ^this } // unless you change the address its the same instr

	*initClass {
		Class.initClassTree(Document);
		// default is relative to your doc directory
		if(dir.isNil,{ dir = Document.dir ++ "Instr/"; });
	}
	init { arg specs,outsp;
		if(path.isNil,{
			path = thisProcess.nowExecutingPath; //  ?? { Document.current.path };
		});
		specs = specs ? #[];
		if(specs.isKindOf(SequenceableCollection).not,{
			Error("Specs should be of type array or nil.").throw
		});
		this.makeSpecs(specs ? #[]);
		if(outsp.isNil,{
			outSpec = nil;
		},{
			outSpec = outsp.asSpec;
			if(outSpec.isNil,{
				("Out spec not found: " + outsp.asCompileString + "for" + this).warn
			});
		});
		this.class.put(this);
		this.class.changed(this)
	}
	makeSpecs { arg argspecs;
		explicitSpecs = argspecs ? [];
		specs =
			Array.fill(this.argsSize,{ arg i;
				var sp,name;
				name = this.argNameAt(i);
				sp = explicitSpecs.at(i);
				// backwards compatibility with old spec style
				if(sp.isSequenceableCollection,{
					// [\envperc]
					// [[0,1]]
					// [StaticSpec()]
					// [0,1]
					if(sp.first.isNumber,{
						sp = sp.asSpec;
					},{
						sp = (sp.first ? name).asSpec
					});
				},{
					sp = (sp ? name).asSpec ?? {ControlSpec.new};
				});
				sp
			});
	}
	// a filter is any instr that has an input the same spec as its output
	isFilter { ^this.specs.any({ |sp| sp == this.outSpec }) }
	
	guiClass { ^InstrGui }
	
}


// an object that points to an Instr and is saveable/loadable as a compile string
// rarely needed these days.  to be deprec.
InstrAt {
	var <>name,<>path,instr;
	*new { arg name;
		^super.new.name_(name).init
	}
	init {
		instr = Instr.at(name);
	}
	value { arg ... args; instr.valueArray(args) }
	valueEnvir { arg ... args; ^instr.valueEnvir(args) }
	prepareToBundle { arg group,bundle;
		instr.prepareToBundle(group,bundle);
	}
	asDefName { ^instr.asDefName }
	storeArgs { ^[name] }

}


// make a virtual Instr by reading the *ar and *kr method def
// eg Patch(SinOsc,[ 440 ])

UGenInstr {

	var <ugenClass,<rate,<specs;

	*new { arg ugenClass,rate=\ar;
		^super.new.init(ugenClass,rate)
	}
	storeArgs {
		^[ugenClass , rate ]
	}
	init { arg uc,r;
		ugenClass = uc.asClass;
		rate = r;

		//specs
		specs = this.argNames.collect({ arg ag,i;
		    var da,sp;
			ag.asSpec ?? {
			    da = this.defArgAt(i) ? 0;
			    if(da.isNumber) {
    			    if(da.inclusivelyBetween(0.0,1.0),{
    				    //("UGenInstr:init Spec.specs has no entry for: % so guessing default ControlSpec".format(ag.asCompileString)).warn;
    				    nil.asSpec
    				},{
    				    sp = ControlSpec(da,da,default:da);
    				    ("UGenInstr:init Spec.specs has no entry for: % so creating spec: %".format(ag.asCompileString,sp)).warn;
    				    sp
    				});
    			} {
    			    ObjectSpec.new
    			};
			}
		});
	}

	value { arg args;
		^ugenClass.performList(rate,args)
	}
	valueArray { arg args;
		^ugenClass.performList(rate,args)
	}

	ar { arg ... args; ^this.value(args) }
	kr { arg ... args; ^this.value(args) }
	outSpec {
		^rate.switch(
	            \ar,\audio,
	            \kr,\control,
	            \new,\fft // temp hack
	            );
	}
	dotNotation { ^ugenClass }
	funcDef { 
	    ^ugenClass.class.findMethod(rate) ?? {
	        ugenClass.superclasses.do { arg sc;
	            var fd;
	            if(sc == UGen) {
	                ^nil
	            };
	            fd = sc.class.findMethod(rate);
	            if(fd.notNil,{
	                ^fd
	            });
	        }
	    };
	}
	path { ^ugenClass.filenameSymbol.asString }
	maxArgs { ^this.argsSize }
	argsSize { ^this.funcDef.argNames.size - 1 }
	argNames {
		var an;
		an = this.funcDef.argNames;
		^if(an.isNil,{
			[]
		},{
			an.copyRange(1,an.size - 1)
		})
	}

	//defaultArgs
	defArgs {
		var nn;
		nn=this.funcDef.prototypeFrame;
		^if(nn.notNil,{nn.copyRange(1,nn.size-1)},{[]});
	}

	initAt { arg i;  ^(this.defArgAt(i) ?? {this.specs.at(i).tryPerform(\default)}) }
	argNameAt { arg i;
		var nn;
		nn=this.funcDef.argNames;
		^if(nn.notNil,{nn.at(i + 1)},{nil});
	}
	defArgAt {
		 arg i;
		var nn;
		nn=this.funcDef.prototypeFrame;
		^if(nn.notNil,{nn.at(i + 1)},{nil});
	}

	guiClass { ^UGenInstrGui }
	asString { ^"UGenInstr " ++ ugenClass.name.asString }
	asInstr { ^this }
	name { ^ugenClass.asString }
	
	*leaves { arg rateMethod; // ar kr new
		var ll;
		^Library.atList([this,'leaves',rateMethod ? 'all']) ?? {
			ll = UGen.allSubclasses;
			if(rateMethod.notNil,{
				ll = ll.select({ arg cls; cls.class.findMethod(rateMethod).notNil })
			});
			ll = ll.sort({ arg a,b; a.charPos <= b.charPos });
			ll = ll.sort({ arg a,b; a.filenameSymbol.asString <= b.filenameSymbol.asString });
			ll = ll.collect(UGenInstr(_));
			Library.putList([this,'leaves',rateMethod ? 'all',ll]);
			ll
		}
	}

}

// see Interface
InterfaceDef : Instr {

	var <>onLoad,
		<>onPlay,
		<>onStop,
		<>onFree,

		<>onNoteOn,
		<>onNoteOff,
		<>onPitchBend,
		<>onCC,

		<guiBodyFunction,
		<>keyDownAction,
		<>keyUpAction;

		// do your own views to handle these
		//<>beginDragAction,
		//<>mouseDownAction,
		//<>mouseUpAction,
	gui_ { arg function; guiBodyFunction = function; }

}
