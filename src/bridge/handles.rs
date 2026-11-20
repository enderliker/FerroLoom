/// A slot in an arena. The generation changes whenever the slot is reused, so a handle issued
/// before that reuse is recognised as stale.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct Handle(u64);

impl Handle {
    fn new(index: u32, generation: u32) -> Handle {
        Handle(((generation as u64) << 32) | index as u64)
    }

    fn index(self) -> usize {
        (self.0 & 0xffff_ffff) as usize
    }

    fn generation(self) -> u32 {
        (self.0 >> 32) as u32
    }
}

struct Slot<T> {
    generation: u32,
    value: Option<T>,
}

pub struct HandleArena<T> {
    slots: Vec<Slot<T>>,
    free: Vec<u32>,
    next_generation: u32,
}

impl<T> Default for HandleArena<T> {
    fn default() -> Self {
        Self::new()
    }
}

impl<T> HandleArena<T> {
    pub fn new() -> HandleArena<T> {
        HandleArena {
            slots: Vec::new(),
            free: Vec::new(),
            next_generation: 1,
        }
    }

    pub fn insert(&mut self, value: T) -> Handle {
        let generation = self.take_generation();
        let index = match self.free.pop() {
            Some(index) => {
                self.slots[index as usize] = Slot {
                    generation,
                    value: Some(value),
                };
                index
            }
            None => {
                let index = self.slots.len() as u32;
                self.slots.push(Slot {
                    generation,
                    value: Some(value),
                });
                index
            }
        };
        Handle::new(index, generation)
    }

    pub fn get(&self, handle: Handle) -> Option<&T> {
        let slot = self.slots.get(handle.index())?;
        if slot.generation != handle.generation() {
            return None;
        }
        slot.value.as_ref()
    }

    pub fn get_mut(&mut self, handle: Handle) -> Option<&mut T> {
        let slot = self.slots.get_mut(handle.index())?;
        if slot.generation != handle.generation() {
            return None;
        }
        slot.value.as_mut()
    }

    pub fn remove(&mut self, handle: Handle) -> Option<T> {
        let index = handle.index();
        let slot = self.slots.get_mut(index)?;
        if slot.generation != handle.generation() {
            return None;
        }
        let value = slot.value.take()?;
        let generation = self.next_generation;
        self.next_generation = self.next_generation.wrapping_add(1).max(1);
        slot.generation = generation;
        self.free.push(index as u32);
        Some(value)
    }

    /// Makes every handle issued so far stale, keeping the values.
    pub fn invalidate_all(&mut self) {
        for index in 0..self.slots.len() {
            if self.slots[index].value.is_some() {
                let generation = self.next_generation;
                self.next_generation = self.next_generation.wrapping_add(1).max(1);
                self.slots[index].generation = generation;
            }
        }
    }

    fn take_generation(&mut self) -> u32 {
        let generation = self.next_generation;
        self.next_generation = self.next_generation.wrapping_add(1).max(1);
        generation
    }
}
