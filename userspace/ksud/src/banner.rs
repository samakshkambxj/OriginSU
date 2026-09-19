use figlet_rs::FIGlet;

pub fn print_banner() -> String {
    let logo_printer = FIGlet::small().unwrap();

    logo_printer.convert("OriginSU").unwrap().to_string()
}
