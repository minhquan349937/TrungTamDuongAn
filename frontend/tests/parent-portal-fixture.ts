import type { ParentPortal } from "../src/services/parentPortalApi";
export function emptyParentPortal(): ParentPortal {
  return { linkStatus: "NO_LINKED_STUDENT", guardian: { id: "PH-portal-test", fullName: "Phụ huynh giả lập", phone: "0912000004", email: null },
    children: [], memberships: [], teachers: [], sessions: [], attendance: [], notices: [] };
}
