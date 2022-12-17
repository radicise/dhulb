package Dhulb;

enum Type {
	u8 (8),
	s8 (8),
	u16 (16),
	s16 (16),
	u32 (32),
	s32 (32),
	u64 (64),
	s64 (64),
	f32 (32),
	f64 (64),
	a16 (16, true),
	a32 (32, true),
	a64 (64, true);
	final int size;
	final boolean addressable;
	Type(int s) {
		size = s;
		addressable = false;
	}
	Type(int s, boolean addrsable) {
		size = s;
		addressable = addrsable;
	}
}