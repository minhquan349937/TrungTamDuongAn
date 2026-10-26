export const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const money = (n: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
    n,
  );
export const dateLabel = (d: string) =>
  new Date(d + "T12:00:00").toLocaleDateString("vi-VN");
export const initials = (name: string) =>
  name
    .split(" ")
    .slice(-2)
    .map((n) => n[0])
    .join("");
export const age = (d: string) => {
  const now = new Date(today()),
    born = new Date(d);
  let years = now.getFullYear() - born.getFullYear();
  if (
    now.getMonth() < born.getMonth() ||
    (now.getMonth() === born.getMonth() && now.getDate() < born.getDate())
  )
    years--;
  return years + " tuổi";
};
export const normalize = (s: string) =>
  s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .toLowerCase();
export const uid = (prefix: string) =>
  prefix + "-" + crypto.randomUUID().slice(0, 8);
