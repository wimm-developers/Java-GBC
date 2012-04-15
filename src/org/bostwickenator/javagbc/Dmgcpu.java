package org.bostwickenator.javagbc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Debug;

/*

 MeBoy

 Copyright 2005-2009 Bjorn Carlin
 http://www.arktos.se/

 Based on JavaBoy, COPYRIGHT (C) 2001 Neil Millstone and The Victoria
 University of Manchester. Bluetooth support based on code contributed by
 Martin Neumann.

 This program is free software; you can redistribute it and/or modify it
 under the terms of the GNU General Public License as published by the Free
 Software Foundation; either version 2 of the License, or (at your option)
 any later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 more details.


 You should have received a copy of the GNU General Public License along with
 this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 Place - Suite 330, Boston, MA 02111-1307, USA.

 */

/**
 * This is the main controlling class for the emulation It contains the code to
 * emulate the Z80-like processor found in the Gameboy, and code to provide the
 * locations in CPU address space that points to the correct area of ROM/RAM/IO.
 */
public class Dmgcpu implements Runnable {
	// Constants for flags register

	public static boolean running = true;

	/** Zero flag */
	private static final int F_ZERO = 0x80;
	/** Subtract/negative flag */
	private static final int F_SUBTRACT = 0x40;
	/** Half carry flag */
	private static final int F_HALFCARRY = 0x20;
	/** Carry flag */
	private static final int F_CARRY = 0x10;

	// same in single and double speed:
	protected static final int INSTRS_PER_DIV = 64;

	// single speed values:
	protected static final int BASE_INSTRS_IN_MODE_0 = 51;
	protected static final int BASE_INSTRS_IN_MODE_2 = 20;
	protected static final int BASE_INSTRS_IN_MODE_3 = 43;
	
	//protected static final int BASE_INSTRS_IN_MODE_0 = 51*2;
	//protected static final int BASE_INSTRS_IN_MODE_2 = 20*2;
	//protected static final int BASE_INSTRS_IN_MODE_3 = 43*2;

	/**
	 * Used to set the speed of the emulator. This controls how many
	 * instructions are executed for each horizontal line scanned on the screen.
	 * Multiply by 154 to find out how many instructions per frame.
	 */
	protected int INSTRS_IN_MODE_0 = BASE_INSTRS_IN_MODE_0;
	protected int INSTRS_IN_MODE_2 = BASE_INSTRS_IN_MODE_2;
	protected int INSTRS_IN_MODE_3 = BASE_INSTRS_IN_MODE_3;

	// Constants for interrupts

	/** Vertical blank interrupt */
	public static final int INT_VBLANK = 0x01;

	/** LCD Coincidence interrupt */
	public static final int INT_LCDC = 0x02;

	/** TIMA (programmable timer) interrupt */
	public static final int INT_TIMA = 0x04;

	/** Serial interrupt */
	public static final int INT_SER = 0x08;

	/** P10 - P13 (Joypad) interrupt */
	public static final int INT_P10 = 0x10;

	/** Registers: 8-bit */
	private  int a, b, c, d, e, f;
	/** Registers: 16-bit */
	private int sp, hl;

	// decoder variables
	private byte[] decoderMemory;
	private int localPC;
	private int globalPC;
	private int decoderMaxCruise; // if localPC exceeds this, a new (half)bank
	// should be found

	/** The number of instructions that have been executed since the last reset */
	private int instrCount;

	private int graphicsChipMode; // takes values 0,2,3 -- mode 1 is signaled by
	// line>=144

	private int nextModeTime;
	private int nextTimaOverflow;
	private int nextTimedInterrupt;

	public boolean interruptsEnabled = false;
	public boolean interruptsArmed = false;
	private boolean timaActive;
	private boolean interruptEnableRequested;
	protected boolean p10Requested;
	protected boolean gbcFeatures;
	protected int gbcRamBank;
	protected boolean hdmaRunning;

	
	//Alex
	protected boolean soundOn = false;
	protected SoundChip soundChip ;
	
	// 0,1 = rom bank 0
	// 2,3 = mapped rom bank
	// 4 = vram (read only)
	// 5 = mapped cartram
	// 6 = main ram
	// 7 = main ram again (+ oam+reg)
	public byte[][] memory = new byte[8][];

	// 8kB main system RAM appears at 0xC000 in address space
	// 32kB for GBC
	private byte[] mainRam;

	// sprite ram, at 0xfe00
	public byte[] oam = new byte[0x100];

	/** registers, at 0xff00 */
	public byte[] registers = new byte[0x100];

	/** instrCount at the time register[4] was reset */
	private int divReset;

	private int instrsPerTima = 256;

	/** Current state of the buttons, bit set = pressed. */
	private int buttonState;

	public SimpleGraphicsChip graphicsChip;

	final int lazyLoadingThreshold = 128;

	// Cartridge:
	public String cartName;

	private int cartType;

	/** Contains the complete ROM image of the cartridge */
	// split into halfbanks of 0x2000 bytes
	private byte[][] rom;

	/** Contains the RAM on the cartridge */
	public byte[][] cartRam;

	/** The bank number which is currently mapped at 0x4000 in CPU address space */
	private int currentRomBank = 1;
	int loadedRomBanks; // number of lazily loaded ROM banks, including 0
	private int[] romTouch;

	/**
	 * The RAM bank number which is currently mapped at 0xA000 in CPU address
	 * space
	 */
	private int currentRamBank;

	private boolean mbc1LargeRamMode;
	private boolean cartRamEnabled;

	/** realtime clock */
	public byte[] rtcReg = new byte[5];
	private int lastRtcUpdate; // ms when rtc were synchronized

	private int[] incflags = new int[256];
	private int[] decflags = new int[256];
	
	public static Context mContext;

	private static final int cyclesPerInstr[] = { 1, 3, 2, 2, 1, 1, 2, 1, 5, 2,
			2, 2, 1, 1, 2, 1, 1, 3, 2, 2, 1, 1, 2, 1, 3, 2, 2, 2, 1, 1, 2, 1,
			3, 3, 2, 2, 1, 1, 2, 1, 3, 2, 2, 2, 1, 1, 2, 1, 3, 3, 2, 2, 3, 3,
			3, 1, 3, 2, 2, 2, 1, 1, 2, 1,

			1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1,
			2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1,
			1, 1, 2, 1, 2, 2, 2, 2, 2, 2, 1, 2, 1, 1, 1, 1, 1, 1, 2, 1,

			1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1,
			2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1,
			1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1,

			5, 3, 4, 4, 6, 4, 2, 4, 5, 4, 4, 0, 6, 6, 2, 4, 5, 3, 4, 0, 6, 4,
			2, 4, 5, 4, 4, 0, 6, 0, 2, 4, 3, 3, 2, 0, 0, 4, 2, 4, 4, 1, 4, 0,
			0, 0, 2, 4, 3, 3, 2, 1, 0, 4, 2, 4, 3, 2, 4, 1, 0, 0, 2, 4, };

	private static final int cyclesPerInstrShift[] = { 2, 2, 2, 2, 2, 2, 4, 2,
			2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2,
			4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2,
			2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2,

			2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2,
			3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2,
			2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,

			2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2,
			4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2,
			2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2,

			2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2,
			4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2,
			2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, };

	public Dmgcpu(String cart, InputStream is) {
		cartName = cart;
		initCartridge(is);

		graphicsChip = new SimpleGraphicsChip(this);

		memory[6] = mainRam;
		memory[7] = mainRam;

		interruptsEnabled = false;

		a = gbcFeatures ? 0x11 : 0x01;
		b = 0x00;
		c = 0x13;
		d = 0x00;
		e = 0xd8;
		f = 0xB0;
		hl = 0x014D;
		setPC(0x0100);
		sp = 0xFFFE;

		graphicsChipMode = 0;
		nextModeTime = 0;
		timaActive = false;
		interruptEnableRequested = false;
		nextTimedInterrupt = 0;

		initIncDecFlags();

		ioHandlerReset();
	}

	public Dmgcpu(String cart, InputStream is, byte[] flatState) {
		cartName = cart;
		initCartridge(is);

		graphicsChip = new SimpleGraphicsChip(this);

		memory[6] = mainRam;
		memory[7] = mainRam;

		unflatten(flatState);

		initIncDecFlags();
	}

	private void initIncDecFlags() {
		incflags[0] = F_ZERO + F_HALFCARRY;
		for (int i = 0x10; i < 0x100; i += 0x10)
			incflags[i] = F_HALFCARRY;

	
		decflags[0] = F_ZERO + F_SUBTRACT;
		for (int i = 1; i < 0x100; i++)
			decflags[i] = F_SUBTRACT + (((i & 0x0f) == 0x0f) ? F_HALFCARRY : 0);
		
	}

	public void unflatten(byte[] flatState) {
		int offset = 0;

		int version = flatState[offset++];
		boolean flatGbcFeatures = flatState[offset++] != 0;
		if (version != 1 || flatGbcFeatures != gbcFeatures)
			throw new RuntimeException("Error");

		a = flatState[offset++] & 0xff;
		b = flatState[offset++] & 0xff;
		c = flatState[offset++] & 0xff;
		d = flatState[offset++] & 0xff;
		e = flatState[offset++] & 0xff;
		f = flatState[offset++] & 0xff;
		sp = flatState[offset++] & 0xff;
		sp = (sp << 8) + (flatState[offset++] & 0xff);
		hl = flatState[offset++] & 0xff;
		hl = (hl << 8) + (flatState[offset++] & 0xff);
		int pc = flatState[offset++] & 0xff;
		pc = (pc << 8) + (flatState[offset++] & 0xff);
		// setPC() will be called below to set the fields.

		instrCount = getInt(flatState, offset);
		offset += 4;
		nextModeTime = getInt(flatState, offset);
		offset += 4;
		nextTimaOverflow = getInt(flatState, offset);
		offset += 4;
		nextTimedInterrupt = getInt(flatState, offset);
		offset += 4;

		timaActive = flatState[offset++] != 0;
		graphicsChipMode = flatState[offset++];
		interruptsEnabled = flatState[offset++] != 0;
		interruptsArmed = flatState[offset++] != 0;
		interruptEnableRequested = flatState[offset++] != 0;

		System.arraycopy(flatState, offset, mainRam, 0, mainRam.length);
		offset += mainRam.length;
		System.arraycopy(flatState, offset, oam, 0, 0x00A0);
		offset += 0x00A0;
		System.arraycopy(flatState, offset, registers, 0, 0x0100);
		offset += 0x0100;

		divReset = getInt(flatState, offset);
		offset += 4;
		instrsPerTima = getInt(flatState, offset);
		offset += 4;

		// cartridge
		for (int i = 0; i < cartRam.length; i++) {
			System.arraycopy(flatState, offset, cartRam[i], 0, 0x2000);
			offset += 0x2000;
		}

		currentRomBank = getInt(flatState, offset);
		offset += 4;
		mapRom(currentRomBank);

		currentRamBank = getInt(flatState, offset);
		offset += 4;
		if (currentRamBank != 0)
			mapRam(currentRamBank);

		mbc1LargeRamMode = (flatState[offset++] != 0);
		cartRamEnabled = (flatState[offset++] != 0);

		// realtime clock
		System.arraycopy(flatState, offset, rtcReg, 0, rtcReg.length);
		offset += rtcReg.length;

		offset = graphicsChip.unflatten(flatState, offset);

		if (gbcFeatures) {
			gbcRamBank = flatState[offset++] & 0xff;
			hdmaRunning = flatState[offset++] != 0;

			if ((registers[0x4D] & 0x80) != 0) {
				// double speed
				INSTRS_IN_MODE_0 = BASE_INSTRS_IN_MODE_0;
				INSTRS_IN_MODE_2 = BASE_INSTRS_IN_MODE_2;
				INSTRS_IN_MODE_3 = BASE_INSTRS_IN_MODE_3;
			} else {
				// instrs_in_mode are set correctly already
			}
		}

		setPC(pc);

		if (offset != flatState.length)
			throw new RuntimeException(": " + offset + ", " + flatState.length);
	}

	public byte[] flatten() {
		int size = 53 + mainRam.length + 0x01A0 + 0x2000 * cartRam.length
				+ rtcReg.length + 0x2000 + 48;
		if (gbcFeatures) {
			size += 2 + 129 + 0x2000;
		}

		byte[] flatState = new byte[size];
		int offset = 0;

		flatState[offset++] = (byte) 1; // version
		flatState[offset++] = (byte) (gbcFeatures ? 1 : 0);

		flatState[offset++] = (byte) a;
		flatState[offset++] = (byte) b;
		flatState[offset++] = (byte) c;
		flatState[offset++] = (byte) d;
		flatState[offset++] = (byte) e;
		flatState[offset++] = (byte) f;
		flatState[offset++] = (byte) (sp >> 8);
		flatState[offset++] = (byte) sp;
		flatState[offset++] = (byte) (hl >> 8);
		flatState[offset++] = (byte) hl;
		int pc = localPC + globalPC;
		flatState[offset++] = (byte) (pc >> 8);
		flatState[offset++] = (byte) pc;

		setInt(flatState, offset, instrCount);
		offset += 4;
		setInt(flatState, offset, nextModeTime);
		offset += 4;
		setInt(flatState, offset, nextTimaOverflow);
		offset += 4;
		setInt(flatState, offset, nextTimedInterrupt);
		offset += 4;

		flatState[offset++] = (byte) (timaActive ? 1 : 0);
		flatState[offset++] = (byte) (graphicsChipMode);
		flatState[offset++] = (byte) (interruptsEnabled ? 1 : 0);
		flatState[offset++] = (byte) (interruptsArmed ? 1 : 0);
		flatState[offset++] = (byte) (interruptEnableRequested ? 1 : 0);

		System.arraycopy(mainRam, 0, flatState, offset, mainRam.length);
		offset += mainRam.length;
		System.arraycopy(oam, 0, flatState, offset, 0x00A0);
		offset += 0x00A0;
		System.arraycopy(registers, 0, flatState, offset, 0x0100);
		offset += 0x0100;

		setInt(flatState, offset, divReset);
		offset += 4;
		setInt(flatState, offset, instrsPerTima);
		offset += 4;

		for (int j = 0; j < cartRam.length; j++) {
			System.arraycopy(cartRam[j], 0, flatState, offset, 0x2000);
			offset += 0x2000;
		}

		setInt(flatState, offset, currentRomBank);
		offset += 4;
		setInt(flatState, offset, currentRamBank);
		offset += 4;

		flatState[offset++] = (byte) (mbc1LargeRamMode ? 1 : 0);
		flatState[offset++] = (byte) (cartRamEnabled ? 1 : 0);

		System.arraycopy(rtcReg, 0, flatState, offset, rtcReg.length);
		offset += rtcReg.length;

		offset = graphicsChip.flatten(flatState, offset);

		if (gbcFeatures) {
			flatState[offset++] = (byte) gbcRamBank;
			flatState[offset++] = (byte) (hdmaRunning ? 1 : 0);
		}

		if (offset != flatState.length)
			throw new RuntimeException("error#21: " + offset + ", "
					+ flatState.length);

		return flatState;
	}

	/**
	 * Perform a CPU address space read. This maps all the relevant objects into
	 * the correct parts of the memory
	 */
	public final int addressRead(int addr) {
		if (addr < 0xa000) {
			return memory[addr >> 13][addr & 0x1fff];
		} else if (addr < 0xc000) {
			if (currentRamBank >= 8) { // real time clock
				rtcSync();
				return rtcReg[currentRamBank - 8];
			} else
				return memory[addr >> 13][addr & 0x1fff];
		} else if ((addr & 0x1000) == 0) {
			return mainRam[addr & 0x0fff];
		} else if (addr < 0xfe00) {
			return mainRam[(addr & 0x0fff) + gbcRamBank * 0x1000];
		} else if (addr < 0xFF00) {
			if (addr > 0xFEA0) // outside OAM range
				return 0xff;
			return (oam[addr - 0xFE00] & 0xFF);
		} else {
			return ioRead(addr - 0xFF00);
		}
	}

	public static final void setInt(byte[] b, int i, int v) {
		b[i++] = (byte) (v >> 24);
		b[i++] = (byte) (v >> 16);
		b[i++] = (byte) (v >> 8);
		b[i++] = (byte) (v);
	}

	public static final int getInt(byte[] b, int i) {
		int r = b[i++] & 0xFF;
		r = (r << 8) + (b[i++] & 0xFF);
		r = (r << 8) + (b[i++] & 0xFF);
		return (r << 8) + (b[i++] & 0xFF);
	}

	/**
	 * Performs a CPU address space write. Maps all of the relevant object into
	 * the right parts of memory.
	 */
	public final void addressWrite(int addr, int data) {
		int bank = addr >> 12;
		switch (bank) {
		case 0x0:
		case 0x1:
		case 0x2:
		case 0x3:
		case 0x4:
		case 0x5:
		case 0x6:
		case 0x7:
			cartridgeWrite(addr, data);
			break;

		case 0x8:
		case 0x9:
			graphicsChip.addressWrite(addr - 0x8000, (byte) data);
			break;

		case 0xA:
		case 0xB:
			cartridgeWrite(addr, data);
			break;

		case 0xC:
			mainRam[addr - 0xC000] = (byte) data;
			break;

		case 0xD:
			mainRam[addr - 0xD000 + gbcRamBank * 0x1000] = (byte) data;
			break;

		case 0xE:
			mainRam[addr - 0xE000] = (byte) data;
			break;

		case 0xF:
			if (addr < 0xFE00) {
				mainRam[addr - 0xF000 + gbcRamBank * 0x1000] = (byte) data;
			} else if (addr < 0xFF00) {
				oam[addr - 0xFE00] = (byte) data;
			} else {
				ioWrite(addr - 0xFF00, data);
			}
			break;
		}
	}

	private final void pushPC() {
		int pc = globalPC + localPC;
		if ((sp >> 13) == 6) {
			mainRam[--sp - 0xC000] = (byte) (pc >> 8);
			mainRam[--sp - 0xC000] = (byte) (pc);
		} else {
			addressWrite(--sp, pc >> 8);
			addressWrite(--sp, pc & 0xFF);
		}
	}

	private final void popPC() {
		if (sp >> 13 == 6) {
			setPC((mainRam[sp++ - 0xc000] & 0xff)
					+ ((mainRam[sp++ - 0xc000] & 0xff) << 8));
		} else {
			setPC((addressRead(sp++) & 0xff)
					+ ((addressRead(sp++) & 0xff) << 8));
		}
	}

	/** Performs a read of a register by internal register number */
	private final int registerRead(int regNum) {
		switch (regNum) {
		case 0:
			return b;
		case 1:
			return c;
		case 2:
			return d;
		case 3:
			return e;
		case 4:
			return (hl >> 8);
		case 5:
			return (hl & 0xFF);
		case 6:
			return addressRead(hl) & 0xff;
		case 7:
			return a;
		default:
			return -1;
		}
	}

	/** Performs a write of a register by internal register number */
	private final void registerWrite(int regNum, int data) {
		switch (regNum) {
		case 0:
			b = data;
			return;
		case 1:
			c = data;
			return;
		case 2:
			d = data;
			return;
		case 3:
			e = data;
			return;
		case 4:
			// h
			hl = (hl & 0x00FF) | (data << 8);
			return;
		case 5:
			// l
			hl = (hl & 0xFF00) | data;
			return;
		case 6:
			// (hl)
			addressWrite(hl, data);
			return;
		case 7:
			a = data;
			return;
		default:
			return;
		}
	}

	private void performHdma() {
		int dmaSrc = ((registers[0x51] & 0xff) << 8)
				+ ((registers[0x52] & 0xff) & 0xF0);
		int dmaDst = ((registers[0x53] & 0x1F) << 8) + (registers[0x54] & 0xF0)
				+ 0x8000;

		for (int r = 0; r < 16; r++) {
			addressWrite(dmaDst + r, addressRead(dmaSrc + r));
		}

		dmaSrc += 16;
		dmaDst += 16;
		registers[0x51] = (byte) ((dmaSrc & 0xFF00) >> 8);
		registers[0x52] = (byte) (dmaSrc & 0x00F0);
		registers[0x53] = (byte) ((dmaDst & 0x1F00) >> 8);
		registers[0x54] = (byte) (dmaDst & 0x00F0);

		if (registers[0x55] == 0) {
			hdmaRunning = false;
		}
		registers[0x55]--;
	}

	/**
	 * If an interrupt is enabled an the interrupt register shows that it has
	 * occured, jump to the relevant interrupt vector address
	 */
	private final void checkInterrupts() {
		pushPC();

		int mask = registers[0xff] & registers[0x0f];

		if ((mask & INT_VBLANK) != 0) {
			setPC(0x40);
			registers[0x0f] -= INT_VBLANK;
		} else if ((mask & INT_LCDC) != 0) {
			setPC(0x48);
			registers[0x0f] -= INT_LCDC;
		} else if ((mask & INT_TIMA) != 0) {
			setPC(0x50);
			registers[0x0f] -= INT_TIMA;
		} else if ((mask & INT_SER) != 0) {
			setPC(0x58);
			registers[0x0f] -= INT_SER;
		} else if ((mask & INT_P10) != 0) {
			setPC(0x60);
			registers[0x0f] -= INT_P10;
		} else {
			// throw new RuntimeException("concurrent modification exception: "
			// + mask + " "
			// + registers[0xff] + " " + registers[0x0f]);
		}

		interruptsEnabled = false;
		interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;
	}

	/** Check for interrupts that need to be initiated */
	private final void initiateInterrupts() {
		
	
		if (instrCount - nextModeTime >= 0) {
			// changed graphics chip mode
			if (graphicsChipMode == 3) {
				// entered mode 0 (unless in vblank)
				graphicsChipMode = 0;
				nextModeTime += INSTRS_IN_MODE_0;

				int line = registers[0x44] & 0xff;

				if (line < 144) {
					if (gbcFeatures && hdmaRunning) {
						performHdma();
					}

					if (((registers[0x40] & 0x80) != 0)
							&& ((registers[0xff] & INT_LCDC) != 0)) {
						if (((registers[0x41] & 0x08) != 0)) {
							// trigger "mode 0 entered" interrupt
							interruptsArmed = true;
							registers[0x0f] |= INT_LCDC;
						}
					}
				}
			} else if (graphicsChipMode == 0) {
				// entered mode 2 (or mode 1, i.e. vblank)
				graphicsChipMode = 2;
				nextModeTime += INSTRS_IN_MODE_2;

				registers[0x44]++;
				if ((registers[0x44] & 0xff) == 154) {
					registers[0x44] = 0;
					if (soundOn) soundChip.outputSound();
				}

				int line = registers[0x44] & 0xff;

				// check for mode 2 interrupt
				if (line < 144) {
					if (((registers[0x41] & 0x20) != 0)) {
						// trigger "mode 2 entered" interrupt
						interruptsArmed = true;
						registers[0x0f] |= INT_LCDC;
					}
				}

				// check for lyc coincidence interrupt
				if (((registers[0x40] & 0x80) != 0)
						&& ((registers[0xff] & INT_LCDC) != 0)) {
					if (((registers[0x41] & 0x40) != 0)
							&& ((registers[0x45] & 0xff) == line)) {
						// trigger "lyc coincidence" interrupt
						interruptsArmed = true;
						registers[0x0f] |= INT_LCDC;
					}
				}

				if (line == 144) {
					// whole frame done, draw buffer and start vblank
					graphicsChip.vBlank();
					if (!running)
						notFinished = false;

					if (((registers[0x40] & 0x80) != 0)
							&& ((registers[0xff] & INT_VBLANK) != 0)) {
						interruptsArmed = true;
						registers[0x0f] |= INT_VBLANK;

						if (((registers[0x41] & 0x10) != 0)
								&& ((registers[0xff] & INT_LCDC) != 0)) {
							// VBLANK LCDC
							// armed is already set
							registers[0x0f] |= INT_LCDC;
						}
					}
				}

				if (line == 0) {
					if (p10Requested) {
						p10Requested = false;

						if ((registers[0xff] & INT_P10) != 0) {
							registers[0x0f] |= INT_P10;
						}

						interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;
					}
				}
			} else {
				// entered mode 3 (unless in vblank)
				graphicsChipMode = 3;
				nextModeTime += INSTRS_IN_MODE_3;

				int line = registers[0x44] & 0xff;
				if (line < 144) {
					// send the line to graphic chip
					graphicsChip.notifyScanline(line);
				}
			}
		}

		if (timaActive && instrCount - nextTimaOverflow >= 0) {
			nextTimaOverflow += instrsPerTima
					* (0x100 - (registers[0x06] & 0xff));

			if ((registers[0xff] & INT_TIMA) != 0) {
				interruptsArmed = true;
				registers[0x0f] |= INT_TIMA;
			}
		}

		if (interruptEnableRequested) {
			interruptsEnabled = true;
			interruptEnableRequested = false;
		}

		nextTimedInterrupt = nextModeTime;
		if (timaActive && nextTimaOverflow < nextTimedInterrupt)
			nextTimedInterrupt = nextTimaOverflow;
		
		
	
	}

	public final void setPC(int pc) {
		if (pc < 0xff00) {
			decoderMemory = memory[pc >> 13];
			localPC = pc & 0x1fff;
			globalPC = pc & 0xe000;
			decoderMaxCruise = (pc < 0xe000) ? 0x1ffd : 0x1dfd;
			if (gbcFeatures) {
				if (gbcRamBank > 1 && pc >= 0xC000)
					decoderMaxCruise &= 0x0fff; // can't cruise in switched ram
				// bank
			}
		} else {
			decoderMemory = registers;
			localPC = pc & 0xff;
			globalPC = 0xff00;
			decoderMaxCruise = 0xfd;
		}
	}

	private final void executeShift(int b2) {
		int regNum = b2 & 0x07;
		int data = registerRead(regNum);
		int newf;

		instrCount += cyclesPerInstrShift[b2];

		/*
		 * 00ooorrr = operation ooo on register rrr 01bbbrrr = test bit bbb of
		 * register rrr 10bbbrrr = reset bit bbb of register rrr 11bbbrrr = set
		 * bit bbb of register rrr
		 */

		if ((b2 & 0xC0) == 0) {
			switch ((b2 & 0xF8)) {
			case 0x00: // RLC A
				f = 0;
				if (data >= 0x80) {
					f = F_CARRY;
				}
				data = (data << 1) & 0xff;
				if ((f & F_CARRY) != 0) {
					data |= 1;
				}

				break;
			case 0x08: // RRC A
				f = 0;
				if ((data & 0x01) != 0) {
					f = F_CARRY;
				}
				data >>= 1;
				if ((f & F_CARRY) != 0) {
					data |= 0x80;
				}
				break;
			case 0x10: // RL r
				if (data >= 0x80) {
					newf = F_CARRY;
				} else {
					newf = 0;
				}
				data = (data << 1) & 0xff;

				if ((f & F_CARRY) != 0) {
					data |= 1;
				}
				f = newf;
				break;
			case 0x18: // RR r
				if ((data & 0x01) != 0) {
					newf = F_CARRY;
				} else {
					newf = 0;
				}
				data >>= 1;

				if ((f & F_CARRY) != 0) {
					data |= 0x80;
				}

				f = newf;
				break;
			case 0x20: // SLA r
				f = 0;
				if ((data & 0x80) != 0) {
					f = F_CARRY;
				}

				data = (data << 1) & 0xff;
				break;
			case 0x28: // SRA r
				f = 0;
				if ((data & 0x01) != 0) {
					f = F_CARRY;
				}

				data = (data & 0x80) + (data >> 1); // i.e. duplicate high
				// bit=sign
				break;
			case 0x30: // SWAP r
				data = (((data & 0x0F) << 4) | (data >> 4));
				f = 0;

				break;
			case 0x38: // SRL r
				f = 0;
				if ((data & 0x01) != 0) {
					f = F_CARRY;
				}

				data >>= 1;
				break;
			}

			if (data == 0) {
				f |= F_ZERO;
			}
			registerWrite(regNum, data);
		} else {
			int bitMask = 1 << ((b2 & 0x38) >> 3);

			if ((b2 & 0xC0) == 0x40) { // BIT n, r
				f = ((f & F_CARRY) | F_HALFCARRY);
				if ((data & bitMask) == 0) {
					f |= F_ZERO;
				}
			} else if ((b2 & 0xC0) == 0x80) { // RES n, r
				registerWrite(regNum, (data & (0xFF - bitMask)));
			} else if ((b2 & 0xC0) == 0xC0) { // SET n, r
				registerWrite(regNum, (data | bitMask));
			}
		}
	}

	private final void executeDAA() {
		int upperNibble = (a >> 4) & 0x0f;
		int lowerNibble = a & 0x0f;

		int newf = f & (F_SUBTRACT | F_CARRY);

		if ((f & F_SUBTRACT) == 0) {
			if ((f & F_CARRY) == 0) {
				if ((upperNibble <= 8) && (lowerNibble >= 0xA)
						&& ((f & F_HALFCARRY) == 0)) {
					a += 0x06;
				}

				if ((upperNibble <= 9) && (lowerNibble <= 0x3)
						&& ((f & F_HALFCARRY) != 0)) {
					a += 0x06;
				}

				if ((upperNibble >= 0xA) && (lowerNibble <= 0x9)
						&& ((f & F_HALFCARRY) == 0)) {
					a += 0x60;
					newf |= F_CARRY;
				}

				if ((upperNibble >= 0x9) && (lowerNibble >= 0xA)
						&& ((f & F_HALFCARRY) == 0)) {
					a += 0x66;
					newf |= F_CARRY;
				}

				if ((upperNibble >= 0xA) && (lowerNibble <= 0x3)
						&& ((f & F_HALFCARRY) != 0)) {
					a += 0x66;
					newf |= F_CARRY;
				}

			} else { // carry is set

				if ((upperNibble <= 0x2) && (lowerNibble <= 0x9)
						&& ((f & F_HALFCARRY) == 0)) {
					a += 0x60;
				}

				if ((upperNibble <= 0x2) && (lowerNibble >= 0xA)
						&& ((f & F_HALFCARRY) == 0)) {
					a += 0x66;
				}

				if ((upperNibble <= 0x3) && (lowerNibble <= 0x3)
						&& ((f & F_HALFCARRY) != 0)) {
					a += 0x66;
				}
			}

		} else { // subtract is set

			if ((f & F_CARRY) == 0) {
				if ((upperNibble <= 0x8) && (lowerNibble >= 0x6)
						&& ((f & F_HALFCARRY) != 0)) {
					a += 0xFA;
				}
			} else { // Carry is set
				if ((upperNibble >= 0x7) && (lowerNibble <= 0x9)
						&& ((f & F_HALFCARRY) == 0)) {
					a += 0xA0;
				}

				if ((upperNibble >= 0x6) && (lowerNibble >= 0x6)
						&& ((f & F_HALFCARRY) != 0)) {
					a += 0x9A;
				}
			}
		}

		a &= 0xff;
		if (a == 0)
			newf |= F_ZERO;

		// halfcarry is wrong
		f = newf;
	}

	private final void executeALU(int b1) {
		int operand = registerRead(b1 & 0x07);
		switch ((b1 & 0x38) >> 3) {
		case 1: // ADC A, r
			if ((f & F_CARRY) != 0) {
				operand++;
			}
			// Note! No break!
		case 0: // ADD A, r

			if ((a & 0x0F) + (operand & 0x0F) >= 0x10) {
				f = F_HALFCARRY;
			} else {
				f = 0;
			}

			a += operand;

			if (a > 0xff) {
				f |= F_CARRY;
				a &= 0xff;
			}

			if (a == 0) {
				f |= F_ZERO;
			}
			break;
		case 3: // SBC A, r
			if ((f & F_CARRY) != 0) {
				operand++;
			}
			// Note! No break!
		case 2: // SUB A, r

			f = F_SUBTRACT;

			if ((a & 0x0F) < (operand & 0x0F)) {
				f |= F_HALFCARRY;
			}

			a -= operand;

			if (a < 0) {
				f |= F_CARRY;
				a &= 0xff;
			}
			if (a == 0) {
				f |= F_ZERO;
			}

			break;
		case 4: // AND A, r
			a &= operand;
			if (a == 0) {
				f = F_HALFCARRY + F_ZERO;
			} else {
				f = F_HALFCARRY;
			}
			break;
		case 5: // XOR A, r
			a ^= operand;
			f = (a == 0) ? F_ZERO : 0;
			break;
		case 6: // OR A, r
			a |= operand;
			f = (a == 0) ? F_ZERO : 0;
			break;
		case 7: // CP A, r (compare)
			f = F_SUBTRACT;
			if (a == operand) {
				f |= F_ZERO;
			} else if (a < operand) {
				f |= F_CARRY;
			}
			if ((a & 0x0F) < (operand & 0x0F)) {
				f |= F_HALFCARRY;
			}
			break;
		}
	}

	private static boolean notFinished = true;
	//private boolean started

	
	
	public final void run() {
		try {

			//Debug.startMethodTracing();
			int newf = 0;
			int b1, b2, offset, b3;

			System.gc();
			int startTime = (int) System.currentTimeMillis();

			graphicsChip.timer = startTime;
			notFinished = true;

			while (notFinished) {

				if (localPC <= decoderMaxCruise) {
					b1 = decoderMemory[localPC++] & 0xff;
					offset = decoderMemory[localPC];
					b2 = offset & 0xff;
					b3 = decoderMemory[localPC + 1];
				} else {
					int pc = localPC + globalPC;
					b1 = addressRead(pc++) & 0xff;
					offset = addressRead(pc);
					b2 = offset & 0xff;
					b3 = addressRead(pc + 1);
					setPC(pc);
				}

				switch (b1) {
				case 0x00: // NOP
					break;
				case 0x01: // LD BC, nn
					localPC += 2;
					b = b3 & 0xff;
					c = b2;
					break;
				case 0x02: // LD (BC), A
					addressWrite((b << 8) | c, a);
					break;
				case 0x03: // INC BC
					c++;
					if (c == 0x0100) {
						c = 0;
						b = (b + 1) & 0xff;
					}
					break;
				case 0x04: // INC B
					b = (b + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b];
					break;
				case 0x05: // DEC B
					b = (b - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b];
					break;
				case 0x06: // LD B, nn
					localPC++;
					b = b2;
					break;
				case 0x07: // RLC A
					if (a >= 0x80) {
						f = F_CARRY;
						a = ((a << 1) + 1) & 0xff;
					} else if (a == 0) {
						f = F_ZERO;
					} else {
						a <<= 1;
						f = 0;
					}
					break;
				case 0x08: // LD (nnnn), SP
					localPC += 2;
					newf = ((b3 & 0xff) << 8) + b2;
					addressWrite(newf, sp);
					addressWrite(newf + 1, sp >> 8);
					break;
				case 0x09: // ADD HL, BC
					hl = (hl + ((b << 8) + c));
					if ((hl & 0xffff0000) != 0) {
						f = (f & F_ZERO) | F_CARRY; // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x0A: // LD A, (BC)
					a = addressRead((b << 8) + c) & 0xff;
					break;
				case 0x0B: // DEC BC
					c--;
					if (c < 0) {
						c = 0xFF;
						b = (b - 1) & 0xff;
					}
					break;
				case 0x0C: // INC C
					c = (c + 1) & 0xff;
					f = (f & F_CARRY) | incflags[c];
					break;
				case 0x0D: // DEC C
					c = (c - 1) & 0xff;
					f = (f & F_CARRY) | decflags[c];
					break;
				case 0x0E: // LD C, nn
					localPC++;
					c = b2;
					break;
				case 0x0F: // RRC A
					if ((a & 0x01) == 0x01) {
						f = F_CARRY;
					} else {
						f = 0;
					}
					a >>= 1;
					if ((f & F_CARRY) != 0) {
						a |= 0x80;
					}
					if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0x10: // STOP
					localPC++;

					if (gbcFeatures) {
						if ((registers[0x4D] & 0x01) != 0) {
							int newKey1Reg = registers[0x4D] & 0xFE;
							int multiplier = 1;
							if ((newKey1Reg & 0x80) != 0) {
								newKey1Reg &= 0x7F;
							} else {
								multiplier = 2;
								newKey1Reg |= 0x80;
							}

							INSTRS_IN_MODE_0 = BASE_INSTRS_IN_MODE_0
									* multiplier;
							INSTRS_IN_MODE_2 = BASE_INSTRS_IN_MODE_2
									* multiplier;
							INSTRS_IN_MODE_3 = BASE_INSTRS_IN_MODE_3
									* multiplier;

							registers[0x4D] = (byte) newKey1Reg;
						}
					}

					break;
				case 0x11: // LD DE, nnnn
					localPC += 2;
					d = b3 & 0xff;
					e = b2;
					break;
				case 0x12: // LD (DE), A
					addressWrite((d << 8) + e, a);
					break;
				case 0x13: // INC DE
					e++;
					if (e == 0x0100) {
						e = 0;
						d = (d + 1) & 0xff;
					}
					break;
				case 0x14: // INC D
					d = (d + 1) & 0xff;
					f = (f & F_CARRY) | incflags[d];
					break;
				case 0x15: // DEC D
					d = (d - 1) & 0xff;
					f = (f & F_CARRY) | decflags[d];
					break;
				case 0x16: // LD D, nn
					localPC++;
					d = b2;
					break;
				case 0x17: // RL A
					if ((a & 0x80) != 0) {
						newf = F_CARRY;
					} else {
						newf = 0;
					}
					a <<= 1;

					if ((f & F_CARRY) != 0) {
						a |= 1;
					}

					a &= 0xFF;
					if (a == 0) {
						newf |= F_ZERO;
					}
					f = newf;
					break;
				case 0x18: // JR nn
					localPC += 1 + offset;
					if (localPC < 0 || localPC > decoderMaxCruise) {
						// switch bank
						setPC(localPC + globalPC);
					}

					break;
				case 0x19: // ADD HL, DE
					hl += ((d << 8) + e);
					if ((hl & 0xFFFF0000) != 0) {
						f = ((f & F_ZERO) | F_CARRY); // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x1A: // LD A, (DE)
					a = (addressRead((d << 8) + e)) & 0xff;
					break;
				case 0x1B: // DEC DE
					e--;
					if (e < 0) {
						e = 0xFF;
						d = (d - 1) & 0xff;
					}
					break;
				case 0x1C: // INC E
					e = (e + 1) & 0xff;
					f = (f & F_CARRY) | incflags[e];
					break;
				case 0x1D: // DEC E
					e = (e - 1) & 0xff;
					f = (f & F_CARRY) | decflags[e];
					break;
				case 0x1E: // LD E, nn
					localPC++;
					e = b2;
					break;
				case 0x1F: // RR A
					if ((a & 0x01) != 0) {
						newf = F_CARRY;
					} else {
						newf = 0;
					}
					a >>= 1;

					if ((f & F_CARRY) != 0) {
						a |= 0x80;
					}

					if (a == 0) {
						newf |= F_ZERO;
					}
					f = newf;
					break;
				case 0x20: // JR NZ, nn
					if (f < F_ZERO) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC++;
					}
					break;
				case 0x21: // LD HL, nnnn
					localPC += 2;
					hl = ((b3 & 0xff) << 8) + b2;
					break;
				case 0x22: // LD (HL+), A
					addressWrite(hl++, a);
					break;
				case 0x23: // INC HL
					hl = (hl + 1) & 0xFFFF;
					break;
				case 0x24: // INC H
					b2 = ((hl >> 8) + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b2];
					hl = (hl & 0xff) + (b2 << 8);
					break;
				case 0x25: // DEC H
					b2 = ((hl >> 8) - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b2];
					hl = (hl & 0xff) + (b2 << 8);
					break;
				case 0x26: // LD H, nn
					localPC++;
					hl = (hl & 0xFF) | (b2 << 8);
					break;
				case 0x27: // DAA
					executeDAA();
					break;
				case 0x28: // JR Z, nn
					if (f >= F_ZERO) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC++;
					}
					break;
				case 0x29: // ADD HL, HL
					hl *= 2;
					if ((hl & 0xFFFF0000) != 0) {
						f = (f & F_ZERO) | F_CARRY; // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x2A: // LDI A, (HL)
					a = addressRead(hl++) & 0xff;
					break;
				case 0x2B: // DEC HL
					hl = (hl - 1) & 0xffff;
					break;
				case 0x2C: // INC L
					b2 = (hl + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b2];
					hl = (hl & 0xff00) + b2;
					break;
				case 0x2D: // DEC L
					b2 = (hl - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b2];
					hl = (hl & 0xff00) + b2;
					break;
				case 0x2E: // LD L, nn
					localPC++;
					hl = (hl & 0xFF00) | b2;
					break;
				case 0x2F: // CPL A
					a = ((~a) & 0x00FF);
					f |= F_SUBTRACT | F_HALFCARRY;
					break;
				case 0x30: // JR NC, nn
					if ((f & F_CARRY) == 0) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC++;
					}
					break;
				case 0x31: // LD SP, nnnn
					localPC += 2;
					sp = ((b3 & 0xff) << 8) + b2;
					break;
				case 0x32:
					addressWrite(hl--, a); // LD (HL-), A
					break;
				case 0x33: // INC SP
					sp = (sp + 1) & 0xFFFF;
					break;
				case 0x34: // INC (HL)
					b2 = (addressRead(hl) + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b2];
					addressWrite(hl, b2);
					break;
				case 0x35: // DEC (HL)
					b2 = (addressRead(hl) - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b2];
					addressWrite(hl, b2);
					break;
				case 0x36: // LD (HL), nn
					localPC++;
					addressWrite(hl, b2);
					break;
				case 0x37: // SCF
					f = (f & F_ZERO) | F_CARRY;
					break;
				case 0x38: // JR C, nn
					if ((f & F_CARRY) != 0) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC += 1;
					}
					break;
				case 0x39: // ADD HL, SP
					hl += sp;
					if (hl > 0x0000FFFF) {
						f = (f & F_ZERO) | F_CARRY; // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x3A: // LD A, (HL-)
					a = addressRead(hl--) & 0xff;
					break;
				case 0x3B: // DEC SP
					sp = (sp - 1) & 0xFFFF;
					break;
				case 0x3C: // INC A
					a = (a + 1) & 0xff;
					f = (f & F_CARRY) | incflags[a];
					break;
				case 0x3D: // DEC A
					a = (a - 1) & 0xff;
					f = (f & F_CARRY) | decflags[a];
					break;
				case 0x3E: // LD A, nn
					localPC++;
					a = b2;
					break;
				case 0x3F: // CCF
					f = (f & (F_CARRY | F_ZERO)) ^ F_CARRY;
					break;

				// B = r
				case 0x40:
					break;
				case 0x41:
					b = c;
					break;
				case 0x42:
					b = d;
					break;
				case 0x43:
					b = e;
					break;
				case 0x44:
					b = hl >> 8;
					break;
				case 0x45:
					b = hl & 0xFF;
					break;
				case 0x46:
					b = addressRead(hl) & 0xff;
					break;
				case 0x47:
					b = a;
					break;

				// C = r
				case 0x48:
					c = b;
					break;
				case 0x49:
					break;
				case 0x4a:
					c = d;
					break;
				case 0x4b:
					c = e;
					break;
				case 0x4c:
					c = hl >> 8;
					break;
				case 0x4d:
					c = hl & 0xFF;
					break;
				case 0x4e:
					c = addressRead(hl) & 0xff;
					break;
				case 0x4f:
					c = a;
					break;

				// D = r
				case 0x50:
					d = b;
					break;
				case 0x51:
					d = c;
					break;
				case 0x52:
					break;
				case 0x53:
					d = e;
					break;
				case 0x54:
					d = hl >> 8;
					break;
				case 0x55:
					d = hl & 0xFF;
					break;
				case 0x56:
					d = addressRead(hl) & 0xff;
					break;
				case 0x57:
					d = a;
					break;

				// E = r
				case 0x58:
					e = b;
					break;
				case 0x59:
					e = c;
					break;
				case 0x5a:
					e = d;
					break;
				case 0x5b:
					break;
				case 0x5c:
					e = hl >> 8;
					break;
				case 0x5d:
					e = hl & 0xFF;
					break;
				case 0x5e:
					e = addressRead(hl) & 0xff;
					break;
				case 0x5f:
					e = a;
					break;

				// h = r
				case 0x60:
					hl = (hl & 0xFF) | (b << 8);
					break;
				case 0x61:
					hl = (hl & 0xFF) | (c << 8);
					break;
				case 0x62:
					hl = (hl & 0xFF) | (d << 8);
					break;
				case 0x63:
					hl = (hl & 0xFF) | (e << 8);
					break;
				case 0x64:
					break;
				case 0x65:
					hl = (hl & 0xFF) * 0x0101;
					break;
				case 0x66:
					hl = (hl & 0xFF) | ((addressRead(hl) & 0xff) << 8);
					break;
				case 0x67:
					hl = (hl & 0xFF) | (a << 8);
					break;

				// l = r
				case 0x68:
					hl = (hl & 0xFF00) | b;
					break;
				case 0x69:
					hl = (hl & 0xFF00) | c;
					break;
				case 0x6a:
					hl = (hl & 0xFF00) | d;
					break;
				case 0x6b:
					hl = (hl & 0xFF00) | e;
					break;
				case 0x6c:
					hl = (hl >> 8) * 0x0101;
					break;
				case 0x6d:
					break;
				case 0x6e:
					hl = (hl & 0xFF00) | (addressRead(hl) & 0xff);
					break;
				case 0x6f:
					hl = (hl & 0xFF00) | a;
					break;

				// (hl) = r
				case 0x70:
					addressWrite(hl, b);
					break;
				case 0x71:
					addressWrite(hl, c);
					break;
				case 0x72:
					addressWrite(hl, d);
					break;
				case 0x73:
					addressWrite(hl, e);
					break;
				case 0x74:
					addressWrite(hl, hl >> 8);
					break;
				case 0x75:
					addressWrite(hl, hl);
					break;
				case 0x76: // HALT
					interruptsEnabled = true;

					if (interruptsArmed) {
						nextTimedInterrupt = instrCount;
					} else {
						while (!interruptsArmed) {
							instrCount = nextTimedInterrupt;
							initiateInterrupts();
							//Thread.yield();
						}
						instrCount++;
						nextTimedInterrupt = instrCount;
					}

					break;
				case 0x77:
					addressWrite(hl, a);
					break;

				// LD A, n:
				case 0x78:
					a = b;
					break;
				case 0x79:
					a = c;
					break;
				case 0x7a:
					a = d;
					break;
				case 0x7b:
					a = e;
					break;
				case 0x7c:
					a = (hl >> 8);
					break;
				case 0x7d:
					a = (hl & 0xFF);
					break;
				case 0x7e:
					a = addressRead(hl) & 0xff;
					break;
				case 0x7f:
					break;

				// ALU, 0x80 - 0xbf

				case 0xA7: // AND A, A
					if (a == 0) {
						f = F_HALFCARRY + F_ZERO;
					} else {
						f = F_HALFCARRY;
					}
					break;

				case 0xAF: // XOR A, A (== LD A, 0)
					a = 0;
					f = F_ZERO;
					break;
				case 0xC0: // RET NZ
					if (f < F_ZERO) {
						popPC();
					}
					break;
				case 0xC1: // POP BC
					c = addressRead(sp++) & 0xff;
					b = addressRead(sp++) & 0xff;
					break;
				case 0xC2: // JP NZ, nnnn
					if (f < F_ZERO) {
						setPC(((b3 & 0xff) << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xC3: // JP nnnn
					setPC(((b3 & 0xff) << 8) + b2);
					break;
				case 0xC4: // CALL NZ, nnnn
					localPC += 2;
					if (f < F_ZERO) {
						pushPC();
						setPC(((b3 & 0xff) << 8) + b2);
					}
					break;
				case 0xC5: // PUSH BC
					addressWrite(--sp, b);
					addressWrite(--sp, c);
					break;
				case 0xC6: // ADD A, nn
					localPC++;
					if ((a & 0x0F) + (b2 & 0x0F) >= 0x10) {
						f = F_HALFCARRY;
					} else {
						f = 0;
					}

					a += b2;

					if (a > 0xff) {
						f |= F_CARRY;
						a &= 0xff;
					}

					if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xC7: // RST 00
					pushPC();
					setPC(0x00);
					break;
				case 0xC8: // RET Z
					if (f >= F_ZERO) {
						popPC();
					}
					break;
				case 0xC9: // RET
					popPC();
					break;
				case 0xCA: // JP Z, nnnn
					if (f >= F_ZERO) {
						setPC(((b3 & 0xff) << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xCB: // Shift/bit test
					localPC++;
					executeShift(b2);
					break;
				case 0xCC: // CALL Z, nnnn
					localPC += 2;
					if (f >= F_ZERO) {
						pushPC();
						setPC(((b3 & 0xff) << 8) + b2);
					}
					break;
				case 0xCD: // CALL nnnn
					localPC += 2;
					pushPC();
					setPC(((b3 & 0xff) << 8) + b2);
					break;
				case 0xCE: // ADC A, nn
					localPC++;

					if ((f & F_CARRY) != 0) {
						b2++;
					}
					if ((a & 0x0F) + (b2 & 0x0F) >= 0x10) {
						f = F_HALFCARRY;
					} else {
						f = 0;
					}

					a += b2;

					if (a > 0xff) {
						f |= F_CARRY;
						a &= 0xff;
					}

					if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xCF: // RST 08
					pushPC();
					setPC(0x08);
					break;
				case 0xD0: // RET NC
					if ((f & F_CARRY) == 0) {
						popPC();
					}
					break;
				case 0xD1: // POP DE
					e = addressRead(sp++) & 0xff;
					d = addressRead(sp++) & 0xff;
					break;
				case 0xD2: // JP NC, nnnn
					if ((f & F_CARRY) == 0) {
						setPC(((b3 & 0xff) << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xD4: // CALL NC, nnnn
					localPC += 2;
					if ((f & F_CARRY) == 0) {
						pushPC();
						setPC(((b3 & 0xff) << 8) + b2);
					}
					break;
				case 0xD5: // PUSH DE
					addressWrite(--sp, d);
					addressWrite(--sp, e);
					break;
				case 0xD6: // SUB A, nn
					localPC++;

					f = F_SUBTRACT;

					if ((a & 0x0F) < (b2 & 0x0F)) {
						f |= F_HALFCARRY;
					}

					a -= b2;

					if (a < 0) {
						f |= F_CARRY;
						a &= 0xff;
					} else if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xD7: // RST 10
					pushPC();
					setPC(0x10);
					break;
				case 0xD8: // RET C
					if ((f & F_CARRY) != 0) {
						popPC();
					}
					break;
				case 0xD9: // RETI
					interruptsEnabled = true;
					if (interruptsArmed) {
						nextTimedInterrupt = instrCount;
					}
					popPC();
					break;
				case 0xDA: // JP C, nnnn
					if ((f & F_CARRY) != 0) {
						setPC(((b3 & 0xff) << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xDC: // CALL C, nnnn
					localPC += 2;
					if ((f & F_CARRY) != 0) {
						pushPC();
						setPC(((b3 & 0xff) << 8) + b2);
					}
					break;
				case 0xDE: // SBC A, nn
					localPC++;
					if ((f & F_CARRY) != 0) {
						b2++;
					}

					f = F_SUBTRACT;
					if ((a & 0x0F) < (b2 & 0x0F)) {
						f |= F_HALFCARRY;
					}

					a -= b2;

					if (a < 0) {
						f |= F_CARRY;
						a &= 0xff;
					} else if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xDF: // RST 18
					pushPC();
					setPC(0x18);
					break;
				case 0xE0: // LDH (FFnn), A
					localPC++;
					ioWrite(b2, a);
					break;
				case 0xE1: // POP HL
					hl = ((addressRead(sp + 1) & 0xff) << 8)
							+ (addressRead(sp) & 0xff);
					sp += 2;
					break;
				case 0xE2: // LDH (FF00 + C), A
					ioWrite(c, a);
					break;
				case 0xE5: // PUSH HL
					addressWrite(--sp, hl >> 8);
					addressWrite(--sp, hl);
					break;
				case 0xE6: // AND nn
					localPC++;
					a &= b2;
					if (a == 0)
						f = F_ZERO;
					else
						f = 0;
					break;
				case 0xE7: // RST 20
					pushPC();
					setPC(0x20);
					break;
				case 0xE8: // ADD SP, nn
					localPC++;

					sp += offset;
					f = 0;
					if (sp > 0xffff || sp < 0) {
						sp &= 0xffff;
						f = F_CARRY;
					}
					break;
				case 0xE9: // JP (HL)
					setPC(hl);
					break;
				case 0xEA: // LD (nnnn), A
					localPC += 2;
					addressWrite(((b3 & 0xff) << 8) + b2, a);
					break;
				case 0xEE: // XOR A, nn
					localPC++;
					a ^= b2;
					f = 0;
					if (a == 0)
						f = F_ZERO;
					break;
				case 0xEF: // RST 28
					pushPC();
					setPC(0x28);
					break;
				case 0xF0: // LDH A, (FFnn)
					localPC++;
					a = ioRead(b2) & 0xff; // fixme, direct access?

					break;
				case 0xF1: // POP AF
					f = addressRead(sp++) & 0xff; // fixme, f0 or ff?
					a = addressRead(sp++) & 0xff;
					break;
				case 0xF2: // LD A, (FF00 + C)
					a = ioRead(c) & 0xff; // fixme, direct access?
					break;
				case 0xF3: // DI
					interruptsEnabled = false;
					break;
				case 0xF5: // PUSH AF
					addressWrite(--sp, a);
					addressWrite(--sp, f);
					break;
				case 0xF6: // OR A, nn
					localPC++;
					a |= b2;
					f = 0;
					if (a == 0) {
						f = F_ZERO;
					}
					break;
				case 0xF7: // RST 30
					pushPC();
					setPC(0x30);
					break;
				case 0xF8: // LD HL, SP + nn ** HALFCARRY FLAG NOT SET ***
					localPC++;
					hl = (sp + offset);
					f = 0;
					if ((hl & 0xffff0000) != 0) {
						f = F_CARRY;
						hl &= 0xFFFF;
					}
					break;
				case 0xF9: // LD SP, HL
					sp = hl;
					break;
				case 0xFA: // LD A, (nnnn)
					localPC += 2;
					a = addressRead(((b3 & 0xff) << 8) + b2) & 0xff;
					break;
				case 0xFB: // EI
					interruptEnableRequested = true;
					nextTimedInterrupt = instrCount + cyclesPerInstr[b1] + 1; // fixme,
					// this
					// is
					// an
					// ugly
					// hack
					break;
				case 0xFE: // CP nn
					localPC++;
					f = ((a & 0x0F) < (b2 & 0x0F)) ? F_HALFCARRY | F_SUBTRACT
							: F_SUBTRACT;
					if (a == b2) {
						f |= F_ZERO;
					} else if (a < b2) {
						f |= F_CARRY;
					}
					break;
				case 0xFF: // RST 38
					pushPC();
					setPC(0x38);
					break;

				default:
					if ((b1 & 0xC0) == 0x80) { // Byte 0x10?????? indicates ALU
						// op, i.e. 0x80 - 0xbf
						executeALU(b1);
					} else {
						throw new RuntimeException(Integer.toHexString(b1));
					}
				}

				instrCount += cyclesPerInstr[b1];

				if (instrCount - nextTimedInterrupt >= 0) {
					initiateInterrupts();

					if (interruptsArmed && interruptsEnabled) {
						checkInterrupts();
					}
				}
			}
			//Debug.stopMethodTracing();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		RomController.saveTime = System.currentTimeMillis();
		RomController.saveCartRam();
		RomController.saveState("temp");
	}

	// IOHandler

	/** Initialize IO to initial power on state */
	private final void ioHandlerReset() {
		ioWrite(0x0F, 0x01);
		ioWrite(0x26, 0xf1);
		ioWrite(0x40, 0x91);
		ioWrite(0x47, 0xFC);
		ioWrite(0x48, 0xFF);
		ioWrite(0x49, 0xFF);
		registers[0x55] = (byte) 0x80;
		hdmaRunning = false;
	}

	/** Read data from IO Ram */
	private final int ioRead(int num) {
		if (num == 0x41) {
			// LCDSTAT
			int output = registers[0x41];

			if (registers[0x44] == registers[0x45]) {
				output |= 4;
			}

			if ((registers[0x44] & 0xff) >= 144) {
				output |= 1; // mode 1
			} else {
				output |= graphicsChipMode;
			}

			return output;
		} else if (num == 0x04) {
			// DIV
			return (byte) ((instrCount - divReset - 1) / INSTRS_PER_DIV);
		} else if (num == 0x05) {
			// TIMA
			if (!timaActive)
				return registers[num];

			return ((instrCount + instrsPerTima * 0x100 - nextTimaOverflow) / instrsPerTima);
		}

		return registers[num];
	}

	/** Write data to IO Ram */
	public void ioWrite(int num, int data) {
		switch (num) {
		case 0x00: // FF00 - Joypad
			int output = 0;
			if ((data & 0x10) == 0) {
				// P14
				output |= buttonState & 0x0f;
			}

			if ((data & 0x20) == 0) {
				// P15
				output |= buttonState >> 4;
			}
			// the high nybble is unused for reading (according to gbcpuman),
			// but Japanese Pokemon
			// seems to require it to be set to f
			registers[0x00] = (byte) (0xf0 | (~output & 0x0f));

			break;

		case 0x02: // Serial
			registers[0x02] = (byte) data;

			if ((registers[0x02] & 0x01) == 1) {
				registers[0x01] = (byte) 0xFF; // when no LAN connection, always
				// receive 0xFF from port.
				// Simulates empty socket.
				if ((registers[0xff] & INT_SER) != 0) {
					interruptsArmed = true;
					registers[0x0f] |= INT_SER;

					if (interruptsEnabled)
						nextTimedInterrupt = instrCount;
				}
				registers[0x02] &= 0x7F;
			}

			break;

		case 0x04: // DIV
			divReset = instrCount;
			break;

		case 0x05: // TIMA
			if (timaActive)
				nextTimaOverflow = instrCount + instrsPerTima
						* (0x100 - (data & 0xff));
			break;

		case 0x07: // TAC
			if ((data & 0x04) != 0) {
				if (!timaActive) {
					timaActive = true;
					nextTimaOverflow = instrCount + instrsPerTima
							* (0x100 - (registers[0x05] & 0xff));
				}

				instrsPerTima = 4 << (2 * ((data - 1) & 3));
				// 0-3 -> {256, 4, 16, 64}
			} else {
				if (timaActive) {
					timaActive = false;
					registers[0x05] = (byte) ((instrCount + instrsPerTima
							* 0x100 - nextTimaOverflow) / instrsPerTima);
				}
			}
			registers[num] = (byte) data;
			break;

		case 0x0f:
			registers[num] = (byte) data;
			interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;

			if (interruptsArmed && interruptsEnabled)
				nextTimedInterrupt = instrCount;
			break;

			
			
			
			
		// sound registers: 10 11 12 13 14 16 17 18 19 1a 1b 1c 1d 1e 20 21 22
		// 23 24 25 26 30-3f

		// 0x10: Sound 1 freq sweep
		// 0x11: Sound 1 length
			

		   case 0x10 :           // Sound channel 1, sweep
		    if (soundOn)
		     soundChip.channel1.setSweep(
		        (Utils.unsign(data) & 0x70) >> 4,
		        (Utils.unsign(data) & 0x07),
		        (Utils.unsign(data) & 0x08) == 1);
		    registers[0x10] = (byte) data;
		    break;

		   case 0x11 :           // Sound channel 1, length and wave duty
		    if (soundOn) {
		     soundChip.channel1.setDutyCycle((Utils.unsign(data) & 0xC0) >> 6);
		     soundChip.channel1.setLength(Utils.unsign(data) & 0x3F);
		    }
		    registers[0x11] = (byte) data;
		    break;

		   case 0x12 :           // Sound channel 1, volume envelope
		    if (soundOn) {
		     soundChip.channel1.setEnvelope(
		      (Utils.unsign(data) & 0xF0) >> 4,
		      (Utils.unsign(data) & 0x07),
		      (Utils.unsign(data) & 0x08) == 8);
		    }
		    registers[0x12] = (byte) data;
		    break;

		   case 0x13 :           // Sound channel 1, frequency low
		    registers[0x13] = (byte) data;
		    if (soundOn) {
		     soundChip.channel1.setFrequency(
		         ((int) (Utils.unsign(registers[0x14]) & 0x07) << 8) + Utils.unsign(registers[0x13]));
		    }
		    break;

		   case 0x14 :           // Sound channel 1, frequency high
		    registers[0x14] = (byte) data;

		    if (soundOn) {
		     if ((registers[0x14] & 0x80) != 0) {
		      soundChip.channel1.setLength(Utils.unsign(registers[0x11]) & 0x3F);
		      soundChip.channel1.setEnvelope(
		       (Utils.unsign(registers[0x12]) & 0xF0) >> 4,
		       (Utils.unsign(registers[0x12]) & 0x07),
		       (Utils.unsign(registers[0x12]) & 0x08) == 8);
		     }
		     if ((registers[0x14] & 0x40) == 0) {
		      soundChip.channel1.setLength(-1);
		     }

		     soundChip.channel1.setFrequency(
		         ((int) (Utils.unsign(registers[0x14]) & 0x07) << 8) + Utils.unsign(registers[0x13]));
		    }
		    break;

		   case 0x17 :           // Sound channel 2, volume envelope
		    if (soundOn) {
		     soundChip.channel2.setEnvelope(
		      (Utils.unsign(data) & 0xF0) >> 4,
		      (Utils.unsign(data) & 0x07),
		      (Utils.unsign(data) & 0x08) == 8);
		    }
		    registers[0x17] = (byte) data;
		    break;

		   case 0x18 :           // Sound channel 2, frequency low
		    registers[0x18] = (byte) data;
		    if (soundOn) {
		     soundChip.channel2.setFrequency(
		        ((int) (Utils.unsign(registers[0x19]) & 0x07) << 8) + Utils.unsign(registers[0x18]));
		    }
		    break;

		   case 0x19 :           // Sound channel 2, frequency high
		    registers[0x19] = (byte) data;

		    if (soundOn) {
		     if ((registers[0x19] & 0x80) != 0) {
		      soundChip.channel2.setLength(Utils.unsign(registers[0x21]) & 0x3F);
		      soundChip.channel2.setEnvelope(
		       (Utils.unsign(registers[0x17]) & 0xF0) >> 4,
		       (Utils.unsign(registers[0x17]) & 0x07),
		       (Utils.unsign(registers[0x17]) & 0x08) == 8);
		     }
		     if ((registers[0x19] & 0x40) == 0) {
		      soundChip.channel2.setLength(-1);
		     }
		     soundChip.channel2.setFrequency(
		         ((int) (Utils.unsign(registers[0x19]) & 0x07) << 8) + Utils.unsign(registers[0x18]));
		    }
		    break;

		   case 0x16 :           // Sound channel 2, length and wave duty
		    if (soundOn) {
		     soundChip.channel2.setDutyCycle((Utils.unsign(data) & 0xC0) >> 6);
		     soundChip.channel2.setLength(Utils.unsign(data) & 0x3F);
		    }
		    registers[0x16] = (byte) data;
		    break;

		   case 0x1A :           // Sound channel 3, on/off
		    if (soundOn) {
		     if ((Utils.unsign(data) & 0x80) != 0) {
		      soundChip.channel3.setVolume((Utils.unsign(registers[0x1C]) & 0x60) >> 5);
		     } else {
		      soundChip.channel3.setVolume(0);
		     }
		    }
//		    System.out.println("Channel 3 enable: " + data);
		    registers[0x1A] = (byte) data;
		    break;

		   case 0x1B :           // Sound channel 3, length
//		    System.out.println("D:" + data);
		    registers[0x1B] = (byte) data;
		    if (soundOn) soundChip.channel3.setLength(Utils.unsign(data));
		    break;

		   case 0x1C :           // Sound channel 3, volume
		    registers[0x1C] = (byte) data;
		    if (soundOn) soundChip.channel3.setVolume((Utils.unsign(registers[0x1C]) & 0x60) >> 5);
		    break;

		   case 0x1D :           // Sound channel 3, frequency lower 8-bit
		    registers[0x1D] = (byte) data;
		    if (soundOn) soundChip.channel3.setFrequency(
		        ((int) (Utils.unsign(registers[0x1E]) & 0x07) << 8) + Utils.unsign(registers[0x1D]));
		    break;

		   case 0x1E :           // Sound channel 3, frequency higher 3-bit
		    registers[0x1E] = (byte) data;
		    if (soundOn) {
		     if ((registers[0x19] & 0x80) != 0) {
		      soundChip.channel3.setLength(Utils.unsign(registers[0x1B]));
		     }
		     soundChip.channel3.setFrequency(
		         ((int) (Utils.unsign(registers[0x1E]) & 0x07) << 8) + Utils.unsign(registers[0x1D]));
		    }
		    break;

		   case 0x20 :           // Sound channel 4, length
		    if (soundOn) soundChip.channel4.setLength(Utils.unsign(data) & 0x3F);
		    registers[0x20] = (byte) data;
		    break;


		   case 0x21 :           // Sound channel 4, volume envelope
		    if (soundOn) soundChip.channel4.setEnvelope(
		      (Utils.unsign(data) & 0xF0) >> 4,
		      (Utils.unsign(data) & 0x07),
		      (Utils.unsign(data) & 0x08) == 8);
		    registers[0x21] = (byte) data;
		    break;

		   case 0x22 :           // Sound channel 4, polynomial parameters
		    if (soundOn) soundChip.channel4.setParameters(
		      (Utils.unsign(data) & 0x07),
		      (Utils.unsign(data) & 0x08) == 8,
		      (Utils.unsign(data) & 0xF0) >> 4);
			registers[0x22] = (byte) data;
		    break;

		   case 0x23 :          // Sound channel 4, initial/consecutive
		    registers[0x23] = (byte) data;
		    if (soundOn) {
		     if ((registers[0x23] & 0x80) != 0) {
		      soundChip.channel4.setLength(Utils.unsign(registers[0x20]) & 0x3F);
		     }
		     if ((registers[0x23] & 0x40) == 0) {
		      soundChip.channel4.setLength(-1);
		     }
		    }
		    break;

		   case 0x25 :           // Stereo select
		    int chanData;

		    registers[0x25] = (byte) data;

		    if (soundOn) {
		     chanData = 0;
		     if ((Utils.unsign(data) & 0x01) != 0) {
		      chanData |= SquareWaveGenerator.CHAN_LEFT;
		     }
		     if ((Utils.unsign(data) & 0x10) != 0) {
		      chanData |= SquareWaveGenerator.CHAN_RIGHT;
		     }
		     soundChip.channel1.setChannel(chanData);

		     chanData = 0;
		     if ((Utils.unsign(data) & 0x02) != 0) {
		      chanData |= SquareWaveGenerator.CHAN_LEFT;
		     }
		     if ((Utils.unsign(data) & 0x20) != 0) {
		      chanData |= SquareWaveGenerator.CHAN_RIGHT;
		     }
		     soundChip.channel2.setChannel(chanData);

		     chanData = 0;
		     if ((Utils.unsign(data) & 0x04) != 0) {
		      chanData |= SquareWaveGenerator.CHAN_LEFT;
		     }
		     if ((Utils.unsign(data) & 0x40) != 0) {
		      chanData |= SquareWaveGenerator.CHAN_RIGHT;
		     }
		     soundChip.channel3.setChannel(chanData);
		    }

		    break;
			
/*
		case 0x12: // Sound 1 volume + volume sweep
			registers[num] = (byte) data;
			break;

		// 0x13: Sound 1 freq 0-7

		case 0x14: // Sound 1 frequency 8-10 + control
			registers[num] = (byte) data;
			break;

		// 0x15: Unused
		// 0x16: Sound 2 length

		case 0x17: // Sound 2 volume + volume sweep
			registers[num] = (byte) data;
			break;

		// 0x18: Sound 2 freq 0-7

		case 0x19: // Sound 2 frequency 8-10 + control
			registers[num] = (byte) data;
			break;

		case 0x1a:
			// sound mode 3, on/off
			registers[num] = (byte) data;
			if ((data & 0x80) == 0) {
				registers[0x26] &= 0xfb; // clear bit 2 of sound status register
			}
			break;

		// 0x1b: Sound 3 length

		case 0x1c: // Sound 3 volume
			registers[num] = (byte) data;
			break;

		// 0x1d: Sound 3 freq 0-7

		case 0x1e: // Sound 3 frequency 8-10 + control
			registers[num] = (byte) data;
			break;

		case 0x1f:
		case 0x20:
		case 0x21:
		case 0x22:
		case 0x23:
		case 0x24:
		case 0x25:
			*/
		case 0x26:
		case 0x27:
		case 0x28:
		case 0x29:
		case 0x2a:
		case 0x2b:
		case 0x2c:
		case 0x2d:
		case 0x2e:
		case 0x2f:
		case 0x30:
		case 0x31:
		case 0x32:
		case 0x33:
		case 0x34:
		case 0x35:
		case 0x36:
		case 0x37:
		case 0x38:
		case 0x39:
		case 0x3a:
		case 0x3b:
		case 0x3c:
		case 0x3d:
		case 0x3e:
		case 0x3f:
			registers[num] = (byte) data;
			break;

		// 0x20-0x23: Sound 4

		// 0x24: Channel control

		// 0x25: Selection of sound terminal

		// 0x26: Sound on/off

		// 0x30-0x3f: Sound 3 wave pattern

		case 0x40: // LCDC
			graphicsChip.UpdateLCDCFlags(data);
			registers[num] = (byte) data;
			break;

		case 0x41: // LCDC
			registers[num] = (byte) (data & 0xf8);
			break;

		case 0x46: // DMA
			System.arraycopy(memory[data >> 5], (data << 8) & 0x1f00, oam, 0,
					0xa0);
			// This is meant to be run at the same time as the CPU is executing
			// instructions, but I don't think it's crucial.
			break;

		case 0x47: // FF47 - BKG and WIN palette
			graphicsChip.decodePalette(0, data);
			if (registers[num] != (byte) data) {
				registers[num] = (byte) data;
				graphicsChip.invalidateAll(0);
			}
			break;

		case 0x48: // FF48 - OBJ1 palette
			graphicsChip.decodePalette(4, data);
			if (registers[num] != (byte) data) {
				registers[num] = (byte) data;
				graphicsChip.invalidateAll(1);
			}
			break;

		case 0x49: // FF49 - OBJ2 palette
			graphicsChip.decodePalette(8, data);
			if (registers[num] != (byte) data) {
				registers[num] = (byte) data;
				graphicsChip.invalidateAll(2);
			}
			break;

		case 0x4A: // FF4A - Window Position Y
			if ((data & 0xff) >= 144)
				graphicsChip.stopWindowFromLine();
			registers[num] = (byte) data;
			break;

		case 0x4B: // FF4B - Window Position X
			if ((data & 0xff) >= 167)
				graphicsChip.stopWindowFromLine();
			registers[num] = (byte) data;
			break;

		case 0x4D: // FF4D - KEY1 - CGB Mode Only - Prepare Speed Switch
			if (gbcFeatures) {
				// high bit is read only
				registers[num] = (byte) ((data & 0x7f) + (registers[num] & 0x80));
			} else {
				registers[num] = (byte) data;
			}
			break;

		case 0x4F: // FF4F - VRAM Bank - GBC only
			if (gbcFeatures) {
				graphicsChip.setVRamBank(data & 0x01);
			}
			registers[num] = (byte) data;
			break;

		case 0x55: // FF55 - HDMA5 - GBC only
			if (gbcFeatures) {
				if (!hdmaRunning && (data & 0x80) == 0) {
					int dmaSrc = ((registers[0x51] & 0xff) << 8)
							+ (registers[0x52] & 0xF0);
					int dmaDst = ((registers[0x53] & 0x1F) << 8)
							+ (registers[0x54] & 0xF0);
					int dmaLen = ((data & 0x7F) * 16) + 16;

					for (int r = 0; r < dmaLen; r++) {
						graphicsChip.addressWrite(dmaDst + r,
								(byte) addressRead(dmaSrc + r));
					}
					// fixme, move instrCount?

					registers[0x55] = (byte) 0xff;
				} else if ((data & 0x80) != 0) {
					// start hdma
					hdmaRunning = true;
					registers[0x55] = (byte) (data & 0x7F);
				} else {
					// stop hdma
					hdmaRunning = false;
					registers[0x55] |= 0x80;
				}
			} else {
				registers[num] = (byte) data;
			}
			break;

		case 0x56:
		case 0x57:
		case 0x58:
		case 0x59:
		case 0x5a:
		case 0x5b:
		case 0x5c:
		case 0x5d:
		case 0x5e:
		case 0x5f:
		case 0x60:
		case 0x61:
		case 0x62:
		case 0x63:
		case 0x64:
		case 0x65:
		case 0x66:
		case 0x67:
			registers[num] = (byte) data;
			break;

		case 0x68: // FF68 - Background Palette Index - GBC only
			if (gbcFeatures) {
				registers[0x69] = (byte) graphicsChip
						.getGBCPalette(data & 0x3f);
			}
			registers[num] = (byte) data;
			break;

		case 0x69: // FF69 - Background Palette Data - GBC only
			if (gbcFeatures) {
				graphicsChip.setGBCPalette(registers[0x68] & 0x3f, data & 0xff);

				if (registers[0x68] < 0) { // high bit = autoincrement
					int next = ((registers[0x68] + 1) & 0x3f);
					registers[0x68] = (byte) (next + 0x80);
					registers[0x69] = (byte) graphicsChip.getGBCPalette(next);
				} else {
					registers[num] = (byte) data;
				}
			} else {
				registers[num] = (byte) data;
			}
			break;

		case 0x6A: // FF6A - Sprite Palette Index - GBC only
			if (gbcFeatures) {
				registers[0x6B] = (byte) graphicsChip
						.getGBCPalette((data & 0x3f) + 0x40);
			}
			registers[0x6A] = (byte) data;
			break;

		case 0x6B: // FF6B - Sprite Palette Data - GBC only
			if (gbcFeatures) {
				graphicsChip.setGBCPalette((registers[0x6A] & 0x3f) + 0x40,
						data & 0xff);

				if (registers[0x6A] < 0) { // high bit = autoincrement
					int next = ((registers[0x6A] + 1) & 0x3f);
					registers[0x6A] = (byte) (next + 0x80);
					registers[0x6B] = (byte) graphicsChip
							.getGBCPalette(next + 0x40);
				} else {
					registers[num] = (byte) data;
				}
			} else {
				registers[num] = (byte) data;
			}
			break;

		case 0x70: // FF70 - GBC Work RAM bank
			if (gbcFeatures) {
				if ((data & 0x07) < 2) {
					gbcRamBank = 1;
				} else {
					gbcRamBank = data & 0x07;
				}

				if (globalPC >= 0xC000) {
					// verify cruising if executing in RAM
					setPC(globalPC + localPC);
				}
			}
			registers[num] = (byte) data;
			break;

		case 0x71:
		case 0x72:
		case 0x73:
		case 0x74:
		case 0x75:
		case 0x76:
		case 0x77:
		case 0x78:
		case 0x79:
		case 0x7a:
		case 0x7b:
		case 0x7c:
		case 0x7d:
		case 0x7e:
		case 0x7f:
		case 0x80:
		case 0x81:
		case 0x82:
		case 0x83:
		case 0x84:
		case 0x85:
		case 0x86:
		case 0x87:
		case 0x88:
		case 0x89:
		case 0x8a:
		case 0x8b:
		case 0x8c:
		case 0x8d:
		case 0x8e:
		case 0x8f:
		case 0x90:
		case 0x91:
		case 0x92:
		case 0x93:
		case 0x94:
		case 0x95:
		case 0x96:
		case 0x97:
		case 0x98:
		case 0x99:
		case 0x9a:
		case 0x9b:
		case 0x9c:
		case 0x9d:
		case 0x9e:
		case 0x9f:
		case 0xa0:
		case 0xa1:
		case 0xa2:
		case 0xa3:
		case 0xa4:
		case 0xa5:
		case 0xa6:
		case 0xa7:
		case 0xa8:
		case 0xa9:
		case 0xaa:
		case 0xab:
		case 0xac:
		case 0xad:
		case 0xae:
		case 0xaf:
		case 0xb0:
		case 0xb1:
		case 0xb2:
		case 0xb3:
		case 0xb4:
		case 0xb5:
		case 0xb6:
		case 0xb7:
		case 0xb8:
		case 0xb9:
		case 0xba:
		case 0xbb:
		case 0xbc:
		case 0xbd:
		case 0xbe:
		case 0xbf:
			registers[num] = (byte) data;
			break;

		case 0xff:
			registers[num] = (byte) data;
			interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;
			if (interruptsArmed && interruptsEnabled)
				nextTimedInterrupt = instrCount;
			break;

		default:
			registers[num] = (byte) data;
			break;
		}
	}

	/** Compressed file types */
	final byte bNotCompressed = 0;
	final byte bZip = 1;
	final byte bJar = 2;
	final byte bGZip = 3;

	public InputStream openRom(String romFileName) {
		byte bFormat;
		boolean bFoundGBROM = false;
		String romName = "None";

		if (romFileName.toUpperCase().indexOf("ZIP") > -1) {
			bFormat = bZip;
		} else if (romFileName.toUpperCase().indexOf("JAR") > -1) {
			bFormat = bZip;
		} else if (romFileName.toUpperCase().indexOf("GZ") > -1) {
			bFormat = bGZip;
		} else {
			bFormat = bNotCompressed;
		}

		// Simplest case, open plain gb or gbc file.
		if (bFormat == bNotCompressed) {
			try {
				return new FileInputStream(new File(romFileName));
			} catch (Exception e) {
				System.out.println("Cant open file");
				return null;
			}
		}

		// Should the ROM be loaded from a ZIP compressed file?
		if (bFormat == bZip) {
			System.out.println("Loading ZIP Compressed ROM");

			java.util.zip.ZipInputStream zip;

			try {

				zip = new java.util.zip.ZipInputStream(
						new java.io.FileInputStream(romFileName));

				// Check for valid files (GB or GBC ending in filename)
				java.util.zip.ZipEntry ze;

				while ((ze = zip.getNextEntry()) != null) {
					String str = ze.getName();
					if (str.toUpperCase().indexOf(".GB") > -1
							|| str.toUpperCase().indexOf(".GBC") > -1) {
						bFoundGBROM = true;
						romName = str;
						// Leave loop if a ROM was found.
						break;
					}
				}
				// Show an error if no ROM file was found in the ZIP
				if (!bFoundGBROM) {
					System.err.println("No GBx ROM found!");
					throw new java.io.IOException("ERROR");
				}
				System.out.println("Found " + romName);

				return zip;
			} catch (Exception e) {
				System.out.println(e);
				return null;
			}
		}

		if (bFormat == bGZip) {
			System.out.println("Loading GZIP Compressed ROM");
			try {
				return new java.util.zip.GZIPInputStream(
						new java.io.FileInputStream(romFileName));
			} catch (Exception e) {
				System.out.println("Can't open file");
				return null;
			}
		}

		// Will never get here
		return null;
	}

	/**
	 * Create a cartridge object, loading ROM and any associated battery RAM
	 * from the cartridge filename given.
	 */
	public static boolean sound = true;
	
	private final void initCartridge(InputStream is) {
		
		//soundOn = sound;
		//System.out.println("Sound " + soundOn);
		
		
		
		if (is == null) {
			throw new RuntimeException(" (" + cartName + ")");
		}
		try {
			byte[] firstBank = new byte[0x2000];

			int total = 0x2000;
			do {
				total -= is.read(firstBank, 0x2000 - total, total); // Read the
				// first
				// bank
				// (bank 0)
			} while (total > 0);

			cartType = firstBank[0x0147] & 0xff;
			int numRomBanks = lookUpCartSize(firstBank[0x0148]); // Determine
			// the
			// number of
			// 16kb rom
			// banks
			gbcFeatures = ((firstBank[0x143] & 0x80) == 0x80);// TODO &&
			// !MeBoy.disableColor;

			if (gbcFeatures) {
				mainRam = new byte[0x8000]; // 32 kB
			} else {
				mainRam = new byte[0x2000]; // 8 kB
			}
			gbcRamBank = 1;

			if (numRomBanks <= lazyLoadingThreshold) {
				rom = new byte[numRomBanks * 2][0x2000]; // Recreate the ROM
				// array with the
				// correct size
				rom[0] = firstBank;

				// Read ROM into memory
				for (int i = 1; i < numRomBanks * 2; i++) {
					if ((i & 15) == 0) {
						// open next file
						// TODO is.close();
						// TODO is = getClass().getResourceAsStream(cartName +
						// (i >> 4));
					}

					total = 0x2000;
					do {
						total -= is.read(rom[i], 0x2000 - total, total);
					} while (total > 0);
				}
			} else {
				rom = new byte[numRomBanks * 2][]; // Recreate the ROM array
				// with the correct size
				rom[0] = firstBank;
				rom[1] = new byte[0x2000];

				// MeBoy.log("Partial loading active.");
				// Read halfbank 1 (second half of bank 0) into memory
				total = 0x2000;
				do {
					total -= is.read(rom[1], 0x2000 - total, total);
				} while (total > 0);
				loadedRomBanks = 1;
			}
			is.close();

			memory[0] = rom[0];
			memory[1] = rom[1];
			romTouch = new int[rom.length / 2];
			mapRom(1);

			int numRamBanks = getNumRAMBanks();

			System.out.println("Loaded '" + cartName + "'. " + numRomBanks
					+ " banks = " + (numRomBanks * 16) + " kB, " + numRamBanks
					+ " RAM banks.");
			System.out.println("Type: " + cartType
					+ (gbcFeatures ? " (color)" : " (bw)"));

			if (numRamBanks == 0)
				numRamBanks = 1; // mbc2 has built-in ram, and anyway we want
			// the memory mapped
			cartRam = new byte[numRamBanks][0x2000];
			memory[5] = cartRam[0];

			lastRtcUpdate = (int) System.currentTimeMillis();
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(" (" + cartName + ", " + ex + ")");
		}
	}

	/** Maps a ROM bank into the CPU address space at 0x4000 */
	private final void mapRom(int bankNo) {
		bankNo = bankNo & ((rom.length >> 1) - 1);
		currentRomBank = bankNo;

		romTouch[bankNo] = instrCount;
		if (rom[bankNo * 2] == null) {
			try {
				byte[][] newmem = new byte[2][];
				if (loadedRomBanks >= lazyLoadingThreshold) {
					// overwrite previous bank

					int candidate = 0; // candidate to be overwritten
					int candidateAge = 0x80000000;

					int bankCount = rom.length >> 1;

					// look for the oldest bank
					for (int i = 1; i < bankCount; i++) {
						if (rom[i * 2] != null) {
							int age = instrCount - romTouch[i];
							if (age > candidateAge) {
								candidateAge = age;
								candidate = i;
							}
						}
					}

					newmem[0] = rom[candidate * 2];
					newmem[1] = rom[candidate * 2 + 1];
					rom[candidate * 2] = null;
					rom[candidate * 2 + 1] = null;
				} else {
					newmem[0] = new byte[0x2000];
					newmem[1] = new byte[0x2000];

					loadedRomBanks++;
				}

				int file = bankNo >> 3;
				int offset = (bankNo & 7) * 0x4000;
				java.io.InputStream is = getClass().getResourceAsStream(
						cartName + file);

				if (is == null || is.skip(offset) != offset)
					throw new RuntimeException("Failed skipping to " + bankNo);

				for (int i = bankNo * 2; i < bankNo * 2 + 2; i++) {
					int total = 0x2000;
					rom[i] = newmem[i & 1];
					do {
						total -= is.read(rom[i], 0x2000 - total, total);
					} while (total > 0);
				}
				// MeBoy.log("loaded bank " + bankNo + " from " + file + " -> "
				// + loadedRomBanks);

				is.close();
			} catch (Exception ex) {
				ex.printStackTrace();

				throw new RuntimeException("error#22, " + ex);
			}
		}

		memory[2] = rom[bankNo * 2];
		memory[3] = rom[bankNo * 2 + 1];
		if ((globalPC & 0xC000) == 0x4000) {
			setPC(localPC + globalPC);
		}
	}

	private final void mapRam(int bankNo) {
		currentRamBank = bankNo;
		if (currentRamBank < cartRam.length)
			memory[5] = cartRam[currentRamBank];
	}

	/**
	 * Writes to an address in CPU address space. Writes to ROM may cause a
	 * mapping change.
	 */
	private final void cartridgeWrite(int addr, int data) {
		int halfbank = addr >> 13;
		int subaddr = addr & 0x1fff;

		switch (cartType) {
		case 0:
			// ROM Only
			break;

		case 1:
		case 2:
		case 3:
			// MBC1
			if (halfbank == 0) {
				cartRamEnabled = ((data & 0x0F) == 0x0A);
			} else if (halfbank == 1) {
				int bankNo = data & 0x1F;
				if (bankNo == 0)
					bankNo = 1;
				mapRom((currentRomBank & 0x60) | bankNo);
			} else if (halfbank == 2) {
				if (mbc1LargeRamMode) {
					mapRam(data & 0x03);
				} else {
					mapRom((currentRomBank & 0x1F) | ((data & 0x03) << 5));
				}
			} else if (halfbank == 3) {
				mbc1LargeRamMode = ((data & 1) == 1);
			} else if (halfbank == 5 && memory[halfbank] != null) {
				// fixme, we should check cartRamEnabled, but that seems
				// to break Pokemon yellow... (which uses MBC5, but I'm erring
				// on the side of caution).
				memory[halfbank][subaddr] = (byte) data;
			}
			break;

		case 5:
		case 6:
			// MBC2
			if ((halfbank == 1)) {
				if ((addr & 0x0100) != 0) {
					int bankNo = data & 0x0F;
					if (bankNo == 0)
						bankNo = 1;
					mapRom(bankNo);
				} else {
					cartRamEnabled = ((data & 0x0F) == 0x0A);
				}
			} else if (halfbank == 5 && memory[halfbank] != null) {
				// fixme, we should check cartRamEnabled, but that seems
				// to break Pokemon yellow... (which uses MBC5, but I'm erring
				// on the side of caution).
				memory[halfbank][subaddr] = (byte) data;
			}

			break;

		case 0x0F:
		case 0x10:
		case 0x11:
		case 0x12:
		case 0x13:
			// MBC3
			if (halfbank == 0) {
				cartRamEnabled = ((data & 0x0F) == 0x0A);
			} else if (halfbank == 1) {
				// Select ROM bank
				int bankNo = data & 0x7F;
				if (bankNo == 0)
					bankNo = 1;
				mapRom(bankNo);
			} else if (halfbank == 2) {
				// Select RAM bank
				if (cartRam.length > 0)
					mapRam(data & 0x0f); // only 0-3 for ram banks, 8+ for RTC
			} else if (halfbank == 3) {
				// fixme, rtc latch
			} else if (halfbank == 5) {
				// memory write
				if (currentRamBank >= 8) {
					// rtc register
					rtcSync();
					rtcReg[currentRamBank - 8] = (byte) data;
				} else if (memory[halfbank] != null) {
					// normal memory
					// fixme, we should check cartRamEnabled, but that seems
					// to break Pokemon yellow... (which uses MBC5, but I'm
					// erring
					// on the side of caution).
					memory[halfbank][subaddr] = (byte) data;
				}
			}
			break;

		case 0x19:
		case 0x1A:
		case 0x1B:
		case 0x1C:
		case 0x1D:
		case 0x1E:
			// MBC5
			if (addr >> 12 == 1) {
				cartRamEnabled = ((data & 0x0F) == 0x0A);
			} else if (addr >> 12 == 2) {
				int bankNo = (currentRomBank & 0xFF00) | data;
				// note: bank 0 can be mapped to 0x4000
				mapRom(bankNo);
			} else if (addr >> 12 == 3) {
				int bankNo = (currentRomBank & 0x00FF) | ((data & 0x01) << 8);
				// note: bank 0 can be mapped to 0x4000
				mapRom(bankNo);
			} else if (halfbank == 2) {
				if (cartRam.length > 0)
					mapRam(data & 0x0f);
			} else if (halfbank == 5) {
				if (memory[halfbank] != null) {
					// fixme, we should check cartRamEnabled, but that seems
					// to break Pokemon yellow...
					memory[halfbank][subaddr] = (byte) data;
				}
			}
			break;
		}
	}

	// Update the RTC registers before reading/writing (if active) with small
	// delta
	protected final void rtcSync() {
		if ((rtcReg[4] & 0x40) == 0) {
			// active
			int now = (int) System.currentTimeMillis();
			while (now - lastRtcUpdate > 1000) {
				lastRtcUpdate += 1000;

				if (++rtcReg[0] == 60) {
					rtcReg[0] = 0;

					if (++rtcReg[1] == 60) {
						rtcReg[1] = 0;

						if (++rtcReg[2] == 24) {
							rtcReg[2] = 0;

							if (++rtcReg[3] == 0) {
								rtcReg[4] = (byte) ((rtcReg[4] | (rtcReg[4] << 7)) ^ 1);
							}
						}
					}
				}
			}
		}
	}

	// Update the RTC registers after resuming (large delta)
	public final void rtcSkip(int s) {
		// seconds
		int sum = s + rtcReg[0];
		rtcReg[0] = (byte) (sum % 60);
		sum = sum / 60;
		if (sum == 0)
			return;

		// minutes
		sum = sum + rtcReg[1];
		rtcReg[1] = (byte) (sum % 60);
		sum = sum / 60;
		if (sum == 0)
			return;

		// hours
		sum = sum + rtcReg[2];
		rtcReg[2] = (byte) (sum % 24);
		sum = sum / 24;
		if (sum == 0)
			return;

		// days, bit 0-7
		sum = sum + (rtcReg[3] & 0xff) + ((rtcReg[4] & 1) << 8);
		rtcReg[3] = (byte) (sum);

		// overflow & day bit 8
		if (sum > 511)
			rtcReg[4] |= 0x80;
		rtcReg[4] = (byte) ((rtcReg[4] & 0xfe) + ((sum >> 8) & 1));
	}

	private final int getNumRAMBanks() {
		switch (rom[0][0x149]) {
		case 1:
		case 2:
			return 1;

		case 3:
			return 4;

		case 4:
		case 5:
		case 6:
			return 16;
		}
		return 0;
	}

	/**
	 * Returns the number of 16Kb banks in a cartridge from the header size
	 * byte.
	 */
	private final int lookUpCartSize(int sizeByte) {
		/**
		 * Translation between ROM size byte contained in the ROM header, and
		 * the number of 16kB ROM banks the cartridge will contain
		 */
		if (sizeByte < 8)
			return 2 << sizeByte;
		else if (sizeByte == 0x52)
			return 72;
		else if (sizeByte == 0x53)
			return 80;
		else if (sizeByte == 0x54)
			return 96;
		return -1;
	}

	public final boolean hasBattery() {
		return (cartType == 3) || (cartType == 9) || (cartType == 0x1B)
				|| (cartType == 0x1E) || (cartType == 6) || (cartType == 0x10)
				|| (cartType == 0x13);
	}

	public void buttonDown(int buttonIndex) {
		buttonState |= 1 << buttonIndex;
		p10Requested = true;
	}

	public void buttonUp(int buttonIndex) {
		buttonState &= 0xff - (1 << buttonIndex);
		p10Requested = true;
	}

	/*
	public void setScale(int screenWidth, int screenHeight) {
		graphicsChip.setScale(screenWidth, screenHeight);
	}*/

	public void releaseReferences() {
		// This code helps the garbage collector on some platforms.
		// (contributed by Alberto Simon)
		incflags = null;
		decflags = null;
		rtcReg = null;
		cartRam = null;
		rom = null;
		romTouch = null;
		cartName = null;
		graphicsChip = null;
		mainRam = null;
		memory = null;
		decoderMemory = null;
		if(soundChip!=null){
			soundChip.stop();
		}
		System.gc();
	}
}
