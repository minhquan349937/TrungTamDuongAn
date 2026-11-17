import { test, expect, type Page } from "@playwright/test";
import { emptyParentPortal } from "./parent-portal-fixture";
import { createSeed } from "../src/mocks/seed";
import type { ParentPortal } from "../src/services/parentPortalApi";
import { today } from "../src/utils/format";

function family(): ParentPortal {
  const data=emptyParentPortal(); data.linkStatus="READY";
  data.children=[
    { id: "HS-real-101", code: "HS101", fullName: "Con A từ database giả lập", birthDate: "2022-02-03", admittedOn: "2024-01-01", status: "ACTIVE" },
    { id: "HS-real-202", code: "HS202", fullName: "Con B từ database giả lập", birthDate: "2021-04-05", admittedOn: "2024-02-01", status: "PAUSED" },
  ];
  data.memberships=data.children.map(child=>({ classId: `CLASS-${child.id}`, code: `LOP-${child.id}`, name: `Lớp ${child.code}`, type: "INDIVIDUAL", teacherId: "GV-real-1", capacity: 1, status: "ACTIVE", studentId: child.id, startedOn: "2024-01-01", endedOn: null }));
  data.teachers=[{ id: "GV-real-1", fullName: "Giáo viên từ database giả lập" }];
  data.sessions=[{ id: "BUOI-real-1", classId: "CLASS-HS-real-101", teacherId: "GV-real-1", startsAt: `${today()}T02:00:00`, endsAt: `${today()}T03:00:00`, location: "Phòng giả lập" }];
  data.attendance=[{ sessionId: "BUOI-real-1", studentId: "HS-real-101", status: "LATE" }];
  return data;
}
async function mock(page: Page, initial=family()) {
  const state={ data: initial, fail: false, loggedIn: true, id: "TK-parent-real-1" };
  await page.route("**/api/**",route=>{
    const path=new URL(route.request().url()).pathname;
    if (path==="/api/auth/csrf") return route.fulfill({json:{token:"csrf"}});
    if (path==="/api/auth/logout") {state.loggedIn=false; return route.fulfill({status:204});}
    if (path==="/api/auth/login") {state.loggedIn=true; state.id="TK-parent-real-2";}
    if (path==="/api/auth/me" || path==="/api/auth/login") return state.loggedIn
      ? route.fulfill({json:{id:state.id,displayName:"Phụ huynh giả lập",phone:"0912000004",email:null,role:"PARENT"}})
      : route.fulfill({status:401,json:{}});
    if (path==="/api/parent/portal") return state.fail
      ? route.fulfill({status:503,json:{message:"Không thể tải hồ sơ từ máy chủ."}})
      : route.fulfill({json:state.data});
    if (path==="/api/schedule") return route.fulfill({json:{
      classes: state.data.memberships.map(m=>({id:m.classId,name:m.name,type:m.type,teacherId:m.teacherId,capacity:m.capacity,status:m.status,startsOn:m.startedOn,endsOn:m.endedOn,teacherAvailable:true})),
      staff: state.data.teachers.map(t=>({id:t.id,name:t.fullName})), students: state.data.children.map(s=>({id:s.id,name:s.fullName})),
      sessions: state.data.sessions.map(s=>({id:s.id,classId:s.classId,teacherId:s.teacherId,date:today(),start:"09:00:00",end:"10:00:00",room:s.location,
        studentIds:state.data.memberships.filter(m=>m.classId===s.classId).map(m=>m.studentId),
        attendance:Object.fromEntries(state.data.attendance.filter(a=>a.sessionId===s.id).map(a=>[a.studentId,a.status]))})),
    }});
    return route.fulfill({json:[]});
  });
  return state;
}

test("parent uses database children instead of PH001 or cached demo, switches siblings and opens real profiles",async({page})=>{
  const seed=createSeed(); const cached=JSON.stringify(seed);
  await page.addInitScript(value=>{localStorage.setItem("duongan.demo.cleared.v1","1");localStorage.setItem("duongan.demo.v1",value);},cached);
  const state=await mock(page);
  await page.goto("/#/dashboard");
  await expect(page.getByRole("button",{name:"Con A từ database giả lập",exact:true})).toBeVisible();
  await expect(page.getByRole("button",{name:seed.students[0].name,exact:true})).toHaveCount(0);
  await page.getByRole("button",{name:"Con A từ database giả lập",exact:true}).click();
  await expect(page.getByRole("dialog")).toContainText("HS101");
  await page.getByRole("dialog").getByRole("button",{name:"Close",exact:true}).click();
  await page.locator(".child-selector .ant-select").click();
  await page.getByTitle("Con B từ database giả lập",{exact:true}).click();
  await expect(page.getByRole("button",{name:"Con B từ database giả lập",exact:true})).toBeVisible();
  await expect(page.getByRole("button",{name:"Con A từ database giả lập",exact:true})).toHaveCount(0);
  state.data.children[1].fullName="Con B đã cập nhật từ database";
  await page.evaluate(()=>window.dispatchEvent(new Event("focus")));
  await expect(page.getByRole("button",{name:"Con B đã cập nhật từ database",exact:true})).toBeVisible();
  expect(await page.evaluate(()=>localStorage.getItem("duongan.demo.v1"))).toBe(cached);
  await page.reload();
  await expect(page.getByRole("button",{name:"Con A từ database giả lập",exact:true})).toBeVisible();
});

test("schedule and attendance come from the owned child's actual sessions",async({page})=>{
  await mock(page); await page.goto("/#/schedule");
  await expect(page.getByRole("heading",{name:"Lớp HS101",exact:true})).toBeVisible();
  await expect(page.getByText("Giáo viên từ database giả lập",{exact:true})).toBeVisible();
  await expect(page.getByText("09:00 – 10:00",{exact:true})).toBeVisible();
  await page.getByRole("button",{name:"Xem điểm danh",exact:true}).click();
  await expect(page.getByRole("dialog")).toContainText("Đi muộn");
  await expect(page.getByRole("dialog")).toContainText("Con A từ database giả lập");
  await expect(page.getByRole("button",{name:"Lưu điểm danh",exact:true})).toHaveCount(0);
});

test("unlinked parent gets a clear empty state even with broken local demo storage",async({page})=>{
  await page.addInitScript(()=>{localStorage.setItem("duongan.demo.cleared.v1","1");localStorage.setItem("duongan.demo.v1","invalid json");});
  await mock(page,emptyParentPortal()); await page.goto("/#/students");
  await expect(page.getByText("Chưa có hồ sơ con được liên kết.",{exact:true})).toBeVisible();
  await expect(page.getByRole("combobox",{name:"Chọn hồ sơ con"})).toBeDisabled();
  await expect(page.getByText("Không thể hiển thị trang",{exact:true})).toHaveCount(0);
});

test("server failure hides old child data and retry recovers without demo fallback",async({page})=>{
  const state=await mock(page); state.fail=true;
  await page.goto("/#/students");
  await expect(page.getByText("Không thể tải hồ sơ của con",{exact:true})).toBeVisible();
  state.fail=false; await page.getByRole("button",{name:"Thử lại",exact:true}).click();
  await expect(page.getByRole("button",{name:"Con A từ database giả lập",exact:true})).toBeVisible();
  state.fail=true; await page.evaluate(()=>window.dispatchEvent(new Event("focus")));
  await expect(page.getByText("Không thể tải hồ sơ của con",{exact:true})).toBeVisible();
  await expect(page.getByText("Con A từ database giả lập",{exact:true})).toHaveCount(0);
});

test("logging into another parent account clears the previous family's state",async({page})=>{
  const state=await mock(page); await page.goto("/#/students");
  await expect(page.getByRole("button",{name:"Con A từ database giả lập",exact:true})).toBeVisible();
  await page.locator(".account-button").click(); await page.getByRole("button",{name:"Đăng xuất",exact:true}).click();
  state.data=family(); state.data.guardian!.id="PH-other-real";
  state.data.children=[{...state.data.children[0],id:"HS-other-real",code:"HS303",fullName:"Con của phụ huynh khác giả lập"}];
  state.data.memberships=[];state.data.sessions=[];state.data.attendance=[];state.data.teachers=[];
  await page.getByLabel("Số điện thoại hoặc email",{exact:true}).fill("0912000005");
  await page.getByLabel("Mật khẩu",{exact:true}).fill("123456");
  await page.getByRole("button",{name:"Đăng nhập",exact:true}).click();
  await expect(page.getByRole("button",{name:"Con của phụ huynh khác giả lập",exact:true})).toBeVisible();
  await expect(page.getByText("Con A từ database giả lập",{exact:true})).toHaveCount(0);
});
