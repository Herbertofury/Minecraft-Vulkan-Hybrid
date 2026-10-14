pub type Result<T> = std::result::Result<T, Box<dyn std::error::Error + Send + Sync>>;
pub mod state { pub mod content_store { pub mod adapters {
#[path = r"C:/Users/Owner/Documents/Codex/2026-10-04/task-4/modrinth-cli-source/packages/app-lib/src/state/content_store/adapters/verified_files.rs"] mod verified_files;
}}}
#[test]
fn sha512_known_answer() { use sha2::{Digest, Sha512}; assert_eq!(format!("{:x}", Sha512::digest(b"abc")), "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"); }
