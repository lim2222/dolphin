// Copyright 2021 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "InputCommon/ControllerInterface/Touch/InputOverrider.h"

#include <array>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <utility>

#include "Common/Assert.h"

#include "Core/HW/GCPad.h"
#include "Core/HW/GCPadEmu.h"
#include "Core/HW/Wiimote.h"
#include "Core/HW/WiimoteEmu/Extension/Classic.h"
#include "Core/HW/WiimoteEmu/Extension/Nunchuk.h"
#include "Core/HW/WiimoteEmu/Extension/TaTaCon.h"
#include "Core/HW/WiimoteEmu/WiimoteEmu.h"

#include "InputCommon/ControllerEmu/ControlGroup/Attachments.h"
#include "InputCommon/ControllerEmu/ControlGroup/ControlGroup.h"
#include "InputCommon/ControllerEmu/ControllerEmu.h"
#include "InputCommon/ControllerEmu/StickGate.h"
#include "InputCommon/ControllerInterface/CoreDevice.h"
#include "InputCommon/InputConfig.h"

namespace ciface::Touch
{
namespace
{
struct InputState
{
  ControlState normal_state = 0;
  ControlState override_state = 0;
  bool overriding = false;
};

using ControlsMap = std::map<std::pair<std::string_view, std::string_view>, ControlID>;
using StateArray = std::array<InputState, ControlID::NUMBER_OF_CONTROLS>;

std::array<StateArray, 4> s_state_arrays;
std::array<ciface::Touch::MotionOverrideState, 4> s_motion_override_states;

const ControlsMap s_gcpad_controls_map = {{
    {{GCPad::BUTTONS_GROUP, GCPad::A_BUTTON}, ControlID::GCPAD_A_BUTTON},
    {{GCPad::BUTTONS_GROUP, GCPad::B_BUTTON}, ControlID::GCPAD_B_BUTTON},
    {{GCPad::BUTTONS_GROUP, GCPad::X_BUTTON}, ControlID::GCPAD_X_BUTTON},
    {{GCPad::BUTTONS_GROUP, GCPad::Y_BUTTON}, ControlID::GCPAD_Y_BUTTON},
    {{GCPad::BUTTONS_GROUP, GCPad::Z_BUTTON}, ControlID::GCPAD_Z_BUTTON},
    {{GCPad::BUTTONS_GROUP, GCPad::START_BUTTON}, ControlID::GCPAD_START_BUTTON},
    {{GCPad::DPAD_GROUP, DIRECTION_UP}, ControlID::GCPAD_DPAD_UP},
    {{GCPad::DPAD_GROUP, DIRECTION_DOWN}, ControlID::GCPAD_DPAD_DOWN},
    {{GCPad::DPAD_GROUP, DIRECTION_LEFT}, ControlID::GCPAD_DPAD_LEFT},
    {{GCPad::DPAD_GROUP, DIRECTION_RIGHT}, ControlID::GCPAD_DPAD_RIGHT},
    {{GCPad::TRIGGERS_GROUP, GCPad::L_DIGITAL}, ControlID::GCPAD_L_DIGITAL},
    {{GCPad::TRIGGERS_GROUP, GCPad::R_DIGITAL}, ControlID::GCPAD_R_DIGITAL},
    {{GCPad::TRIGGERS_GROUP, GCPad::L_ANALOG}, ControlID::GCPAD_L_ANALOG},
    {{GCPad::TRIGGERS_GROUP, GCPad::R_ANALOG}, ControlID::GCPAD_R_ANALOG},
    {{GCPad::MAIN_STICK_GROUP, ControllerEmu::ReshapableInput::X_INPUT_OVERRIDE},
     ControlID::GCPAD_MAIN_STICK_X},
    {{GCPad::MAIN_STICK_GROUP, ControllerEmu::ReshapableInput::Y_INPUT_OVERRIDE},
     ControlID::GCPAD_MAIN_STICK_Y},
    {{GCPad::C_STICK_GROUP, ControllerEmu::ReshapableInput::X_INPUT_OVERRIDE},
     ControlID::GCPAD_C_STICK_X},
    {{GCPad::C_STICK_GROUP, ControllerEmu::ReshapableInput::Y_INPUT_OVERRIDE},
     ControlID::GCPAD_C_STICK_Y},
}};

const ControlsMap s_wiimote_controls_map = {{
    {{WiimoteEmu::Wiimote::BUTTONS_GROUP, WiimoteEmu::Wiimote::A_BUTTON},
     ControlID::WIIMOTE_A_BUTTON},
    {{WiimoteEmu::Wiimote::BUTTONS_GROUP, WiimoteEmu::Wiimote::B_BUTTON},
     ControlID::WIIMOTE_B_BUTTON},
    {{WiimoteEmu::Wiimote::BUTTONS_GROUP, WiimoteEmu::Wiimote::ONE_BUTTON},
     ControlID::WIIMOTE_ONE_BUTTON},
    {{WiimoteEmu::Wiimote::BUTTONS_GROUP, WiimoteEmu::Wiimote::TWO_BUTTON},
     ControlID::WIIMOTE_TWO_BUTTON},
    {{WiimoteEmu::Wiimote::BUTTONS_GROUP, WiimoteEmu::Wiimote::PLUS_BUTTON},
     ControlID::WIIMOTE_PLUS_BUTTON},
    {{WiimoteEmu::Wiimote::BUTTONS_GROUP, WiimoteEmu::Wiimote::MINUS_BUTTON},
     ControlID::WIIMOTE_MINUS_BUTTON},
    {{WiimoteEmu::Wiimote::BUTTONS_GROUP, WiimoteEmu::Wiimote::HOME_BUTTON},
     ControlID::WIIMOTE_HOME_BUTTON},
    {{WiimoteEmu::Wiimote::DPAD_GROUP, DIRECTION_UP}, ControlID::WIIMOTE_DPAD_UP},
    {{WiimoteEmu::Wiimote::DPAD_GROUP, DIRECTION_DOWN}, ControlID::WIIMOTE_DPAD_DOWN},
    {{WiimoteEmu::Wiimote::DPAD_GROUP, DIRECTION_LEFT}, ControlID::WIIMOTE_DPAD_LEFT},
    {{WiimoteEmu::Wiimote::DPAD_GROUP, DIRECTION_RIGHT}, ControlID::WIIMOTE_DPAD_RIGHT},
    {{WiimoteEmu::Wiimote::IR_GROUP, ControllerEmu::ReshapableInput::X_INPUT_OVERRIDE},
     ControlID::WIIMOTE_IR_X},
    {{WiimoteEmu::Wiimote::IR_GROUP, ControllerEmu::ReshapableInput::Y_INPUT_OVERRIDE},
     ControlID::WIIMOTE_IR_Y},
}};

const ControlsMap s_nunchuk_controls_map = {{
    {{WiimoteEmu::Nunchuk::BUTTONS_GROUP, WiimoteEmu::Nunchuk::C_BUTTON},
     ControlID::NUNCHUK_C_BUTTON},
    {{WiimoteEmu::Nunchuk::BUTTONS_GROUP, WiimoteEmu::Nunchuk::Z_BUTTON},
     ControlID::NUNCHUK_Z_BUTTON},
    {{WiimoteEmu::Nunchuk::STICK_GROUP, ControllerEmu::ReshapableInput::X_INPUT_OVERRIDE},
     ControlID::NUNCHUK_STICK_X},
    {{WiimoteEmu::Nunchuk::STICK_GROUP, ControllerEmu::ReshapableInput::Y_INPUT_OVERRIDE},
     ControlID::NUNCHUK_STICK_Y},
}};

const ControlsMap s_classic_controls_map = {{
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::A_BUTTON},
     ControlID::CLASSIC_A_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::B_BUTTON},
     ControlID::CLASSIC_B_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::X_BUTTON},
     ControlID::CLASSIC_X_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::Y_BUTTON},
     ControlID::CLASSIC_Y_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::ZL_BUTTON},
     ControlID::CLASSIC_ZL_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::ZR_BUTTON},
     ControlID::CLASSIC_ZR_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::PLUS_BUTTON},
     ControlID::CLASSIC_PLUS_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::MINUS_BUTTON},
     ControlID::CLASSIC_MINUS_BUTTON},
    {{WiimoteEmu::Classic::BUTTONS_GROUP, WiimoteEmu::Classic::HOME_BUTTON},
     ControlID::CLASSIC_HOME_BUTTON},
    {{WiimoteEmu::Classic::DPAD_GROUP, DIRECTION_UP}, ControlID::CLASSIC_DPAD_UP},
    {{WiimoteEmu::Classic::DPAD_GROUP, DIRECTION_DOWN}, ControlID::CLASSIC_DPAD_DOWN},
    {{WiimoteEmu::Classic::DPAD_GROUP, DIRECTION_LEFT}, ControlID::CLASSIC_DPAD_LEFT},
    {{WiimoteEmu::Classic::DPAD_GROUP, DIRECTION_RIGHT}, ControlID::CLASSIC_DPAD_RIGHT},
    {{WiimoteEmu::Classic::TRIGGERS_GROUP, WiimoteEmu::Classic::L_DIGITAL},
     ControlID::CLASSIC_L_DIGITAL},
    {{WiimoteEmu::Classic::TRIGGERS_GROUP, WiimoteEmu::Classic::R_DIGITAL},
     ControlID::CLASSIC_R_DIGITAL},
    {{WiimoteEmu::Classic::TRIGGERS_GROUP, WiimoteEmu::Classic::L_ANALOG},
     ControlID::CLASSIC_L_ANALOG},
    {{WiimoteEmu::Classic::TRIGGERS_GROUP, WiimoteEmu::Classic::R_ANALOG},
     ControlID::CLASSIC_R_ANALOG},
    {{WiimoteEmu::Classic::LEFT_STICK_GROUP, ControllerEmu::ReshapableInput::X_INPUT_OVERRIDE},
     ControlID::CLASSIC_LEFT_STICK_X},
    {{WiimoteEmu::Classic::LEFT_STICK_GROUP, ControllerEmu::ReshapableInput::Y_INPUT_OVERRIDE},
     ControlID::CLASSIC_LEFT_STICK_Y},
    {{WiimoteEmu::Classic::RIGHT_STICK_GROUP, ControllerEmu::ReshapableInput::X_INPUT_OVERRIDE},
     ControlID::CLASSIC_RIGHT_STICK_X},
    {{WiimoteEmu::Classic::RIGHT_STICK_GROUP, ControllerEmu::ReshapableInput::Y_INPUT_OVERRIDE},
     ControlID::CLASSIC_RIGHT_STICK_Y},
}};

const ControlsMap s_tatacon_controls_map = {{
    {{"Center", "Left"},  ControlID::TATACON_CENTER_LEFT},
    {{"Center", "Right"}, ControlID::TATACON_CENTER_RIGHT},
    {{"Rim",    "Left"},  ControlID::TATACON_RIM_LEFT},
    {{"Rim",    "Right"}, ControlID::TATACON_RIM_RIGHT},
}};

ControllerEmu::InputOverrideFunction GetInputOverrideFunction(const ControlsMap& controls_map,
                                                              size_t i)
{
  StateArray& state_array = s_state_arrays[i];

  return [&](std::string_view group_name, std::string_view control_name,
             ControlState controller_state) -> std::optional<ControlState> {
    const auto it = controls_map.find(std::make_pair(group_name, control_name));
    if (it == controls_map.end())
      return std::nullopt;

    const ControlID control = it->second;
    InputState& input_state = state_array[control];
    if (input_state.normal_state != controller_state)
    {
      input_state.normal_state = controller_state;
      input_state.overriding = false;
    }

    return input_state.overriding ? std::make_optional(input_state.override_state) : std::nullopt;
  };
}

}  // namespace

void RegisterGameCubeInputOverrider(int controller_index)
{
  Pad::GetConfig()
      ->GetController(controller_index)
      ->SetInputOverrideFunction(GetInputOverrideFunction(s_gcpad_controls_map, controller_index));
}

void RegisterWiiInputOverrider(int controller_index)
{
  auto* wiimote =
      static_cast<WiimoteEmu::Wiimote*>(Wiimote::GetConfig()->GetController(controller_index));

  wiimote->SetInputOverrideFunction(
      GetInputOverrideFunction(s_wiimote_controls_map, controller_index));

  auto& attachments = static_cast<ControllerEmu::Attachments*>(
                          wiimote->GetWiimoteGroup(WiimoteEmu::WiimoteGroup::Attachments))
                          ->GetAttachmentList();

  attachments[WiimoteEmu::ExtensionNumber::NUNCHUK]->SetInputOverrideFunction(
      GetInputOverrideFunction(s_nunchuk_controls_map, controller_index));
  attachments[WiimoteEmu::ExtensionNumber::CLASSIC]->SetInputOverrideFunction(
      GetInputOverrideFunction(s_classic_controls_map, controller_index));
  attachments[WiimoteEmu::ExtensionNumber::TATACON]->SetInputOverrideFunction(
      GetInputOverrideFunction(s_tatacon_controls_map, controller_index));
}

void UnregisterGameCubeInputOverrider(int controller_index)
{
  Pad::GetConfig()->GetController(controller_index)->ClearInputOverrideFunction();

  for (size_t i = ControlID::FIRST_GC_CONTROL; i <= ControlID::LAST_GC_CONTROL; ++i)
    s_state_arrays[controller_index][i].overriding = false;
}

void UnregisterWiiInputOverrider(int controller_index)
{
  auto* wiimote =
      static_cast<WiimoteEmu::Wiimote*>(Wiimote::GetConfig()->GetController(controller_index));

  wiimote->ClearInputOverrideFunction();

  auto& attachments = static_cast<ControllerEmu::Attachments*>(
                          wiimote->GetWiimoteGroup(WiimoteEmu::WiimoteGroup::Attachments))
                          ->GetAttachmentList();

  attachments[WiimoteEmu::ExtensionNumber::NUNCHUK]->ClearInputOverrideFunction();
  attachments[WiimoteEmu::ExtensionNumber::CLASSIC]->ClearInputOverrideFunction();

  for (size_t i = ControlID::FIRST_WII_CONTROL; i <= ControlID::LAST_WII_CONTROL; ++i)
    s_state_arrays[controller_index][i].overriding = false;
}

void SetControlState(int controller_index, ControlID control, double state)
{
  // Route motion controls to MotionOverrideState instead of InputOverrideFunction
  MotionOverrideState& m = s_motion_override_states[controller_index];
  switch (control)
  {
  case ControlID::WIIMOTE_SHAKE_X:        m.wiimote_shake_x = state; return;
  case ControlID::WIIMOTE_SHAKE_Y:        m.wiimote_shake_y = state; return;
  case ControlID::WIIMOTE_SHAKE_Z:        m.wiimote_shake_z = state; return;
  case ControlID::WIIMOTE_SWING_X:        m.wiimote_swing_x = state; return;
  case ControlID::WIIMOTE_SWING_Y:        m.wiimote_swing_y = state; return;
  case ControlID::WIIMOTE_SWING_FORWARD:  m.wiimote_swing_forward = state; return;
  case ControlID::WIIMOTE_SWING_BACKWARD: m.wiimote_swing_backward = state; return;
  case ControlID::WIIMOTE_TILT_FORWARD:  m.wiimote_tilt_forward = state; return;
  case ControlID::WIIMOTE_TILT_BACKWARD: m.wiimote_tilt_backward = state; return;
  case ControlID::WIIMOTE_TILT_LEFT:     m.wiimote_tilt_left = state; return;
  case ControlID::WIIMOTE_TILT_RIGHT:    m.wiimote_tilt_right = state; return;
  case ControlID::NUNCHUK_SHAKE_X:        m.nunchuk_shake_x = state; return;
  case ControlID::NUNCHUK_SHAKE_Y:        m.nunchuk_shake_y = state; return;
  case ControlID::NUNCHUK_SHAKE_Z:        m.nunchuk_shake_z = state; return;
  case ControlID::NUNCHUK_SWING_X:        m.nunchuk_swing_x = state; return;
  case ControlID::NUNCHUK_SWING_Y:        m.nunchuk_swing_y = state; return;
  case ControlID::NUNCHUK_SWING_FORWARD:  m.nunchuk_swing_forward = state; return;
  case ControlID::NUNCHUK_SWING_BACKWARD: m.nunchuk_swing_backward = state; return;
  case ControlID::NUNCHUK_TILT_FORWARD:  m.nunchuk_tilt_forward = state; return;
  case ControlID::NUNCHUK_TILT_BACKWARD: m.nunchuk_tilt_backward = state; return;
  case ControlID::NUNCHUK_TILT_LEFT:     m.nunchuk_tilt_left = state; return;
  case ControlID::NUNCHUK_TILT_RIGHT:    m.nunchuk_tilt_right = state; return;
  default: break;
  }

  InputState& input_state = s_state_arrays[controller_index][control];
  input_state.override_state = state;
  input_state.overriding = true;
}

void ClearControlState(int controller_index, ControlID control)
{
  // Clear motion override fields too
  MotionOverrideState& m = s_motion_override_states[controller_index];
  switch (control)
  {
  case ControlID::WIIMOTE_SHAKE_X:        m.wiimote_shake_x = 0; return;
  case ControlID::WIIMOTE_SHAKE_Y:        m.wiimote_shake_y = 0; return;
  case ControlID::WIIMOTE_SHAKE_Z:        m.wiimote_shake_z = 0; return;
  case ControlID::WIIMOTE_SWING_X:        m.wiimote_swing_x = 0; return;
  case ControlID::WIIMOTE_SWING_Y:        m.wiimote_swing_y = 0; return;
  case ControlID::WIIMOTE_SWING_FORWARD:  m.wiimote_swing_forward = 0; return;
  case ControlID::WIIMOTE_SWING_BACKWARD: m.wiimote_swing_backward = 0; return;
  case ControlID::WIIMOTE_TILT_FORWARD:  m.wiimote_tilt_forward = 0; return;
  case ControlID::WIIMOTE_TILT_BACKWARD: m.wiimote_tilt_backward = 0; return;
  case ControlID::WIIMOTE_TILT_LEFT:     m.wiimote_tilt_left = 0; return;
  case ControlID::WIIMOTE_TILT_RIGHT:    m.wiimote_tilt_right = 0; return;
  case ControlID::NUNCHUK_SHAKE_X:        m.nunchuk_shake_x = 0; return;
  case ControlID::NUNCHUK_SHAKE_Y:        m.nunchuk_shake_y = 0; return;
  case ControlID::NUNCHUK_SHAKE_Z:        m.nunchuk_shake_z = 0; return;
  case ControlID::NUNCHUK_SWING_X:        m.nunchuk_swing_x = 0; return;
  case ControlID::NUNCHUK_SWING_Y:        m.nunchuk_swing_y = 0; return;
  case ControlID::NUNCHUK_SWING_FORWARD:  m.nunchuk_swing_forward = 0; return;
  case ControlID::NUNCHUK_SWING_BACKWARD: m.nunchuk_swing_backward = 0; return;
  case ControlID::NUNCHUK_TILT_FORWARD:  m.nunchuk_tilt_forward = 0; return;
  case ControlID::NUNCHUK_TILT_BACKWARD: m.nunchuk_tilt_backward = 0; return;
  case ControlID::NUNCHUK_TILT_LEFT:     m.nunchuk_tilt_left = 0; return;
  case ControlID::NUNCHUK_TILT_RIGHT:    m.nunchuk_tilt_right = 0; return;
  default: break;
  }

  InputState& input_state = s_state_arrays[controller_index][control];
  input_state.overriding = false;
}

const MotionOverrideState& GetMotionOverrideState(int controller_index)
{
  return s_motion_override_states[controller_index];
}

double GetGateRadiusAtAngle(int controller_index, ControlID stick, double angle)
{
  ControllerEmu::ControlGroup* group;

  switch (stick)
  {
  case ControlID::GCPAD_MAIN_STICK_X:
  case ControlID::GCPAD_MAIN_STICK_Y:
    group = Pad::GetGroup(controller_index, PadGroup::MainStick);
    break;
  case ControlID::GCPAD_C_STICK_X:
  case ControlID::GCPAD_C_STICK_Y:
    group = Pad::GetGroup(controller_index, PadGroup::CStick);
    break;
  case ControlID::NUNCHUK_STICK_X:
  case ControlID::NUNCHUK_STICK_Y:
    group = Wiimote::GetNunchukGroup(controller_index, WiimoteEmu::NunchukGroup::Stick);
    break;
  // Motion controls (Swing/Tilt) use a fixed radius, not a gate shape
  case ControlID::WIIMOTE_SWING_X:
  case ControlID::WIIMOTE_SWING_Y:
    return 1.0;
  case ControlID::NUNCHUK_SWING_X:
  case ControlID::NUNCHUK_SWING_Y:
    return 1.0;
  case ControlID::WIIMOTE_IR_X:
  case ControlID::WIIMOTE_IR_Y:
    return 1.0;
  case ControlID::CLASSIC_LEFT_STICK_X:
  case ControlID::CLASSIC_LEFT_STICK_Y:
    group = Wiimote::GetClassicGroup(controller_index, WiimoteEmu::ClassicGroup::LeftStick);
    break;
  case ControlID::CLASSIC_RIGHT_STICK_X:
  case ControlID::CLASSIC_RIGHT_STICK_Y:
    group = Wiimote::GetClassicGroup(controller_index, WiimoteEmu::ClassicGroup::RightStick);
    break;
  default:
    ASSERT(false);
    return 0;
  }

  return static_cast<ControllerEmu::ReshapableInput*>(group)->GetGateRadiusAtAngle(angle);
}
}  // namespace ciface::Touch
