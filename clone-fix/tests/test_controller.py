import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('rotation_candidate', Path(__file__).parents[1] / 'rotate.py')
controller = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controller)


class RotationGuardsTest(unittest.TestCase):
    def test_no_operations_during_generation(self):
        with patch.object(controller, 'generation_busy', return_value=True), patch.object(controller, 'ctl') as ctl:
            self.assertIn('postponed', controller.rotate('video'))
            ctl.assert_not_called()

    def test_remaining_quota_never_deletes(self):
        with patch.object(controller, 'generation_busy', return_value=False), \
                patch.object(controller, 'quota', return_value=('123', 1, 10)), patch.object(controller, 'ctl') as ctl:
            self.assertEqual('Quota remaining: 1/10', controller.rotate('video'))
            ctl.assert_not_called()

    def test_changed_process_never_deletes(self):
        with patch.object(controller, 'generation_busy', return_value=False), \
                patch.object(controller, 'quota', side_effect=[('123', 0, 10), ('456', 0, 10)]), \
                patch.object(controller.time, 'sleep'), patch.object(controller, 'ctl') as ctl:
            with self.assertRaisesRegex(RuntimeError, 'changed'):
                controller.rotate('video')
            ctl.assert_not_called()

    def test_old_installed_bridge_never_receives_rotate(self):
        with patch.object(controller, 'generation_busy', return_value=False), \
                patch.object(controller, 'quota', return_value=('123', 0, 10)), \
                patch.object(controller.time, 'sleep'), patch.object(controller, 'ctl', return_value='ERROR unsupported command') as ctl:
            with self.assertRaisesRegex(RuntimeError, 'lacks durable IDs'):
                controller.rotate('video')
            ctl.assert_called_once_with('capabilities')

    def test_malformed_list_is_not_treated_as_empty(self):
        with patch.object(controller, 'ctl', return_value='bridge unavailable'):
            with self.assertRaisesRegex(RuntimeError, 'Unrecognized'):
                controller.clones()

    def test_empty_list_supported(self):
        with patch.object(controller, 'ctl', return_value='none'):
            self.assertEqual([], controller.clones())

    def test_both_modes_select_exhausted_edit_quota(self):
        with patch.object(controller, 'quota', side_effect=[('123', 7, 10), ('123', 0, 2)]), \
                patch.object(controller, 'rotate', return_value='rotated') as rotate:
            self.assertEqual('rotated', controller.monitor('both'))
            rotate.assert_called_once_with('edit')

    def test_unavailable_quota_is_not_zero(self):
        with patch.object(controller, 'quota', side_effect=[RuntimeError('VM unavailable'), ('123', 1, 2)]), \
                patch.object(controller, 'rotate') as rotate:
            self.assertEqual('No confirmed exhausted quota', controller.monitor('both'))
            rotate.assert_not_called()

    def test_both_modes_select_exhausted_video_quota(self):
        with patch.object(controller, 'quota', side_effect=[('123', 0, 10), ('123', 1, 2)]), \
                patch.object(controller, 'rotate', return_value='rotated') as rotate:
            self.assertEqual('rotated', controller.monitor('both'))
            rotate.assert_called_once_with('video')


if __name__ == '__main__':
    unittest.main()
