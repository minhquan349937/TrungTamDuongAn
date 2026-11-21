export function fileStem(value: string, extension: "xlsx" | "pdf") {
  return value.trim().replace(new RegExp(`\\.${extension}$`, "i"), "")
    .replace(/[\\/:*?"<>|]/g, "-").replace(/[. ]+$/g, "").slice(0, 120);
}

export function reportFileName(studentName: string, evaluatedAt: string) {
  const date = new Date(`${evaluatedAt.slice(0, 10)}T00:00:00`);
  const month = Number.isNaN(date.getTime()) ? "" : ` ${String(date.getMonth() + 1).padStart(2, "0")}-${date.getFullYear()}`;
  return `${studentName} - Báo cáo tháng${month}`;
}
