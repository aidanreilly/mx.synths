// Engine_MxSynths
Engine_MxSynths : CroneEngine {
	// <mx>
	var mxParameters;
	var mxVoices;
	var mxVoicesOn;
	var mxSynthFX;
	var mxBusFx;
	var fnNoteOn, fnNoteOff;
	var pedalSustainOn=false;
	var pedalSostenutoOn=false;
	var pedalSustainNotes;
	var pedalSostenutoNotes;
	// </mx>


	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

	alloc {

		// <mx>
		// initialize variables
		mxParameters=Dictionary.with(*["synth"->"synthy","sub"->1.0,"amp"->1.0,
			"portamento"->0.0,"monophonic"->0.0,
			"pan"->0.0,"tune"->0.0,
			"attack"->1.0,"decay"->0.2,"sustain"->0.9,"release"->5.0,
			"mod1"->0.0,"mod2"->0.0,"mod3"->0.0,"mod4"->0.0]);
		mxVoices=Dictionary.new;
		mxVoicesOn=Dictionary.new;
		pedalSustainNotes=Dictionary.new;
		pedalSostenutoNotes=Dictionary.new;

		// initialize synth defs
		SynthDef("mxfx",{
			arg out=0, inBus=10, 
			lpf=20000, delay=0,
			secondsPerBeat=1,delayBeats=4,delayFeedback=0.1;

			var snd, snd2;

			snd=In.ar(inBus,2);
	
			// // add flanger
			// flanger = snd+LocalIn.ar(2); //add some feedback
			// flanger= DelayN.ar(flanger,0.02,SinOsc.kr(0.1,0,0.005,0.005)); //max delay of 20msec
			// LocalOut.ar(0.5*flanger);
			// snd=SelectX.ar(flang,[snd,flanger]);

			// lpf
			snd = LPF.ar(snd.tanh,Lag.kr(lpf,1));
			
			// delay
			snd = snd + (delay * CombL.ar(
				snd,
				2,
				secondsPerBeat*delayBeats,
				secondsPerBeat*delayBeats*LinLin.kr(delayFeedback,0,1,2,128),// delayFeedback should vary between 2 and 128
			)); 

			// todo:
			// add tremelo
			// env=env*Clip.ar(1+(perturb2val*LFPar.ar(perturb2val*10).range(-1,0)));

			Out.ar(out,snd);
		}).add;

		SynthDef("synthy",{
			arg out=0,hz=220,amp=0.5,gate=1,sub=0,portamento=1,
			attack=1.0,decay=0.2,sustain=0.9,release=5,
			mod1=0,mod2=0,mod3=0,mod4=0,pan=0;
			var snd,note,env;
			note=Lag.kr(hz,portamento).cpsmidi;
			env=EnvGen.ar(Env.adsr(attack,decay,sustain,release),gate,doneAction:2);
			sub=Lag.kr(sub,1);
			snd=Pan2.ar(Pulse.ar((note-12).midicps,LinLin.kr(LFTri.kr(0.5),-1,1,0.2,0.8))*sub);
			snd=snd+Mix.ar({
				var snd2;
				snd2=SawDPW.ar(note.midicps);
				snd2=LPF.ar(snd2,LinExp.kr(SinOsc.kr(rrand(1/30,1/10),rrand(0,2*pi)),-1,1,2000,12000));
				snd2=DelayC.ar(snd2, rrand(0.01,0.03), LFNoise1.kr(Rand(5,10),0.01,0.02)/15 );
				Pan2.ar(snd2,VarLag.kr(LFNoise0.kr(1/3),3,warp:\sine))
			}!2);
			snd = Balance2.ar(snd[0],snd[1],pan);
			Out.ar(out,snd*env*amp/5);
		}).add;

		SynthDef("casio",{
			arg out=0,hz=220,amp=0.5,gate=1,sub=0,portamento=1,
			attack=1.0,decay=0.2,sustain=0.9,release=5,
			mod1=0,mod2=0,mod3=0,mod4=0,pan=0;
			var freq=hz;
			var env=EnvGen.ar(Env.adsr(attack,decay,sustain,release),gate,doneAction:2);
			var freqBase=freq;
			var freqRes=SinOsc.kr(Rand(0,0.2),0).range(freqBase/2,freqBase*2);
			var pdbase=Impulse.ar(freqBase);
			var pd=Phasor.ar(pdbase,2*pi*freqBase/context.server.sampleRate,0,2pi);
			var pdres=Phasor.ar(pdbase,2*pi*freqRes/context.server.sampleRate,0,2pi);
			var pdi=LinLin.ar((2pi-pd).max(0),0,2pi,0,1);
			var snd=Lag.ar(SinOsc.ar(0,pdres)*pdi,1/freqBase).dup;

			snd = Pan2.ar(snd,pan);
			Out.ar(out,snd*env*amp/5);
		}).add;

		// https://github.com/monome/dust/blob/master/lib/sc/Engine_PolyPerc.sc
		SynthDef("PolyPerc",{
			arg out=0,hz=220,amp=0.5,gate=1,sub=0,portamento=1,
			attack=1.0,decay=0.2,sustain=0.9,release=5,
			mod1=0,mod2=0,mod3=0,mod4=0,pan=0;
			var snd = Pulse.ar(hz, 0.5);
			var filt = MoogFF.ar(snd,1000,2);
			var env=EnvGen.ar(Env.adsr(attack,decay,sustain,release),gate,doneAction:2);
			snd = Pan2.ar(snd,pan);
			Out.ar(out,snd*env*amp/10);
		}).add;

		// https://github.com/catfact/zebra/blob/master/lib/Engine_DreadMoon.sc#L20-L41
		SynthDef("piano",{
			arg out=0,hz=220,amp=0.5,pan=0,gate=1,
			sub=0,portamento=1,
			attack=1.0,decay=0.2,sustain=0.9,release=5,
			mod1=0,mod2=0,mod3=0,mod4=0;
			var snd,note,env, damp;
			var noise, string, delaytime, lpf, noise_env, damp_mul;
			var noise_hz = 4000, noise_attack=0.002, noise_decay=0.06,
			tune_up = 1.0005, tune_down = 0.9996, string_decay=3.0,
			lpf_ratio=2.0, lpf_rq = 4.0, hpf_hz = 40, damp_time=0.1;

			hz=Lag.kr(hz,portamento);
			env=EnvGen.ar(Env.adsr(attack,decay,sustain,release),gate,doneAction:2);

			damp=mod1;
			damp_mul = LagUD.ar(K2A.ar(1.0 - damp), 0, damp_time);

			noise_env = Decay2.ar(Impulse.ar(0));
			noise = LFNoise2.ar(noise_hz) * noise_env;

			delaytime = 1.0 / (hz * [tune_up, tune_down]);
			string = Mix.new(CombL.ar(noise, delaytime, delaytime, string_decay * damp_mul));

			snd = RLPF.ar(string, lpf_ratio * hz, lpf_rq);
			snd = HPF.ar(snd, hpf_hz);
			snd = Pan2.ar(snd,pan);
	
			Out.ar(out,snd*env*amp/5);
		}).add;

		// initialize fx synth and bus
		context.server.sync;
		mxBusFx = Bus.audio(context.server,2);
		mxBusFx.postln;
		context.server.sync;
		mxSynthFX = Synth.new("mxfx",[\out,0,\inBus,mxBusFx]);
		context.server.sync;

		// intialize helper functions
		fnNoteOn= {
			arg note,amp;
			var lowestNote=10000;
			var sub=0;
			(mxParameters.at("synth")++" note_on "++note).postln;

			// if monophonic, remove all the other sounds
			if (mxParameters.at("monophonic")>0,{
				"turning off monophonic voices".postln;
				mxVoicesOn.keysValuesDo({ arg key, syn;
					mxVoicesOn.removeAt(key);
					mxVoices.at(key).set(\gate,0);
				});
			});

			// low-note priority for sub oscillator
			mxVoicesOn.keysValuesDo({ arg key, syn;
				if (key<lowestNote,{
					lowestNote=key;
				});
			});
			if (lowestNote<10000,{
				if (note<lowestNote,{
					sub=1;
					mxVoices.at(lowestNote).set(\sub,0);
				},{
					sub=0;
				});
			},{
				sub=1;
			});

			(amp*mxParameters.at("amp")).postln;

			mxVoices.put(note,
				Synth.before(mxSynthFX,mxParameters.at("synth"),[
					\amp,amp*mxParameters.at("amp"),
					\out,mxBusFx,
					\hz,(note+mxParameters.at("tune")).midicps,
					\pan,mxParameters.at("pan"),
					\sub,sub*mxParameters.at("sub"),
					\attack,mxParameters.at("attack"),
					\decay,mxParameters.at("decay"),
					\sustain,mxParameters.at("sustain"),
					\release,mxParameters.at("release"),
					\portamento,mxParameters.at("portamento"),
					\mod1,mxParameters.at("mod1"),
					\mod2,mxParameters.at("mod2"),
					\mod3,mxParameters.at("mod3"),
					\mod4,mxParameters.at("mod4"),
				]);
			);
			mxVoicesOn.put(note,1);
			NodeWatcher.register(mxVoices.at(note));
		};
		
		fnNoteOff = {
			arg note;
			var lowestNote=10000;
			("mx_note_off "++note).postln;

			mxVoicesOn.removeAt(note);

			if (pedalSustainOn==true,{
				pedalSustainNotes.put(note,1);
			},{
				if ((pedalSostenutoOn==true)&&(pedalSostenutoNotes.at(note)!=nil),{
					// do nothing, it is a sostenuto note
				},{
					// remove the sound
					mxVoices.at(note).set(\gate,0);
					// swap sub
					mxVoicesOn.keysValuesDo({ arg key, syn;
						if (key<lowestNote,{
							lowestNote=key;
						});
					});
					if (lowestNote<10000,{
						("swapping sub to "++lowestNote).postln;
						mxVoices.at(note).set(\sub,0);
						mxVoices.at(lowestNote).set(\sub,mxParameters.at("sub"));
					});
				});
			});


		};

		// add norns commands
		this.addCommand("mx_note_on", "if", { arg msg;
			var lowestNote=10000;
			var note=msg[1];
			if (mxVoices.at(note)!=nil,{
				if (mxVoices.at(note).isRunning==true,{
					(mxParameters.at("synth")++" retrigger "++note).postln;
					mxVoices.at(note).set(\gate,0);
					fnNoteOn.(msg[1],msg[2]);
				},{ fnNoteOn.(msg[1],msg[2]); });
			},{  fnNoteOn.(msg[1],msg[2]); });
		});	

		this.addCommand("mx_note_off", "i", { arg msg;
			var note=msg[1];
			if (mxVoices.at(note)!=nil,{
				if (mxVoices.at(note).isRunning==true,{
					fnNoteOff.(note);
				});
			});
		});

		this.addCommand("mx_sustain", "i", { arg msg;
			pedalSustainOn=(msg[1]==1);
			if (pedalSustainOn==false,{
				// release all sustained notes
				pedalSustainNotes.keysValuesDo({ arg note, val; 
					fnNoteOff.(note);
					pedalSustainNotes.removeAt(note);
				});
			});
		});

		this.addCommand("mx_sustenuto", "i", { arg msg;
			pedalSostenutoOn=(msg[1]==1);
			if (pedalSostenutoOn==false,{
				// release all sustained notes
				pedalSostenutoNotes.keysValuesDo({ arg note, val; 
					fnNoteOff.(note);
					pedalSostenutoNotes.removeAt(note);
				});
			},{
				// add currently held notes
				mxVoicesOn.keysValuesDo({ arg note, val;
					pedalSostenutoNotes.put(note,1);
				});
			});
		});

		this.addCommand("mx_fxset","sf",{ arg msg;
			var key=msg[1].asSymbol;
			var val=msg[2];
			mxSynthFX.set(key,val);
		});

		this.addCommand("mx_set_synth","s",{ arg msg;
			var val=msg[1].asSymbol;
			("setting synth to "++val).postln;
			mxParameters.put("synth",val.asSymbol);
		});

		this.addCommand("mx_set","sf",{ arg msg;
			var key=msg[1].asString;
			var val=msg[2];
			("setting "++key++" to "++val).postln;
			mxParameters.put(key,val);
			mxParameters.at("amp").postln;
			switch (key, 
				"sub",{}, 	// do nothing, is special
				"synth",{}, // do nothing
				"amp",{}, 	// do nothing
				{
					mxVoices.keysValuesDo({ arg note, syn;
						if (syn.isRunning==true,{
							syn.set(key.asSymbol,val);
						});
					});
				}
			);
		});
		// </mx>
	}

	free {
		// <mx>
		mxBusFx.free;
		mxSynthFX.free;
		mxVoices.keysValuesDo({ arg key, value; value.free; });
		// </mx>
	}
}