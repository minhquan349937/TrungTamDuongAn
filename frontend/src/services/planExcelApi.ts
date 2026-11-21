export const planExcelApi = {
  download: async (planId: string) => {
    const response = await fetch(`/api/plans/${encodeURIComponent(planId)}/excel`, {
      credentials: "include", cache: "no-store",
    });
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null;
      throw new Error(error?.message ?? "Không thể tải kế hoạch Excel. Vui lòng thử lại.");
    }
    const type = response.headers.get("Content-Type") ?? "";
    if (!type.includes("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
      throw new Error("Máy chủ không trả về tệp Excel hợp lệ.");
    return response.blob();
  },
};
